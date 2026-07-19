package com.gray.anime.payment.application;

import com.gray.anime.common.api.TraceIds;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.eventing.DomainEventPublisher;
import com.gray.anime.eventing.EventRoutes;
import com.gray.anime.eventing.PaymentConfirmedEvent;
import com.gray.anime.payment.application.provider.PaymentCheckout;
import com.gray.anime.payment.application.provider.PaymentProvider;
import com.gray.anime.payment.application.provider.ProviderCheckoutSession;
import com.gray.anime.payment.domain.OrderRecord;
import com.gray.anime.payment.domain.PaymentRecord;
import com.gray.anime.payment.domain.PaymentStatus;
import com.gray.anime.payment.infrastructure.mapper.OrderMapper;
import com.gray.anime.payment.infrastructure.mapper.PaymentMapper;
import com.gray.anime.payment.infrastructure.provider.DemoPaymentProvider;
import com.gray.anime.payment.interfaces.dto.CheckoutSessionRequest;
import com.gray.anime.payment.interfaces.dto.CheckoutSessionView;
import com.gray.anime.payment.interfaces.dto.PaymentView;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PaymentApplicationService {
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final JdbcTemplate jdbc;
    private final DomainEventPublisher events;
    private final PaymentProvider paymentProvider;
    private final MeterRegistry meters;

    public PaymentApplicationService(PaymentMapper paymentMapper, OrderMapper orderMapper, JdbcTemplate jdbc,
                                     DomainEventPublisher events, PaymentProvider paymentProvider,
                                     MeterRegistry meters) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.jdbc = jdbc;
        this.events = events;
        this.paymentProvider = paymentProvider;
        this.meters = meters;
    }

    @Transactional
    public CheckoutSessionView createCheckoutSession(CurrentUser user, CheckoutSessionRequest request) {
        LockedPayment locked = lock(user, request.paymentNo());
        PaymentRecord payment = locked.payment();
        OrderRecord order = locked.order();
        validatePaymentConsistency(payment, order);
        requirePayableOrder(order);

        PaymentStatus current = PaymentStatus.from(payment.getStatus());
        if (current == PaymentStatus.CONFIRMED) {
            throw new BizException("PAYMENT_ALREADY_CONFIRMED", "订单已支付");
        }
        if (current == PaymentStatus.PENDING && hasActiveSession(payment)) {
            return checkoutView(payment, null);
        }
        if (current == PaymentStatus.PENDING) {
            LocalDateTime failedAt = LocalDateTime.now();
            updateStatus(payment, PaymentStatus.PENDING, PaymentStatus.FAILED, "SESSION_EXPIRED", failedAt);
            recordTransition(payment.getPaymentNo(), PaymentStatus.PENDING, PaymentStatus.FAILED,
                    "SESSION_EXPIRED", "SESSION_EXPIRED:" + payment.getPaymentNo() + ":" + payment.getAttemptCount());
            payment.setStatus(PaymentStatus.FAILED.name());
            payment.setFailureCode("SESSION_EXPIRED");
            payment.setUpdatedAt(failedAt);
            current = PaymentStatus.FAILED;
        }
        if (current != PaymentStatus.CREATED && current != PaymentStatus.FAILED && current != PaymentStatus.PENDING) {
            throw paymentStateError(payment, order);
        }

        ProviderCheckoutSession session = paymentProvider.createSession(
                new PaymentCheckout(payment.getPaymentNo(), payment.getOrderNo(), payment.getAmountCents()));
        validateProviderSession(session);

        LocalDateTime now = LocalDateTime.now();
        int assigned = jdbc.update("""
                UPDATE payment
                SET status='PENDING', channel=?, provider_session_id=?, session_expires_at=?, failure_code=NULL,
                    attempt_count=attempt_count + 1, updated_at=?
                WHERE payment_no=? AND user_id=? AND status=?
                """, paymentProvider.code(), session.sessionId(), session.expiresAt(), now,
                payment.getPaymentNo(), user.id(), current.name());
        if (assigned == 0) {
            throw new BizException("PAYMENT_STATE_CHANGED", "支付状态已变化，请刷新订单");
        }
        recordTransition(payment.getPaymentNo(), current, PaymentStatus.PENDING, "CHECKOUT_SESSION",
                "SESSION:" + session.sessionId());

        payment.setStatus(PaymentStatus.PENDING.name());
        payment.setChannel(paymentProvider.code());
        payment.setProviderSessionId(session.sessionId());
        payment.setSessionExpiresAt(session.expiresAt());
        payment.setUpdatedAt(now);
        return checkoutView(payment, session);
    }

    @Transactional
    public PaymentView confirmDemo(CurrentUser user, String paymentNo) {
        requireDemoProvider();
        return confirm(user, paymentNo, DemoPaymentProvider.CODE, "DEMO_CONFIRM:" + paymentNo);
    }

    @Transactional
    public PaymentView confirmLegacyMock(CurrentUser user, String paymentNo) {
        requireDemoProvider();
        PaymentRecord current = ownedPayment(user, paymentNo);
        if (PaymentStatus.from(current.getStatus()) == PaymentStatus.CREATED) {
            createCheckoutSession(user, new CheckoutSessionRequest(paymentNo));
        }
        return confirm(user, paymentNo, DemoPaymentProvider.CODE, "LEGACY_CONFIRM:" + paymentNo);
    }

    @Transactional
    public PaymentView markFailed(CurrentUser user, String paymentNo, String providerCode, String failureCode,
                                  String providerEventId) {
        LockedPayment locked = lock(user, paymentNo);
        PaymentRecord payment = locked.payment();
        if (PaymentStatus.from(payment.getStatus()) == PaymentStatus.FAILED) {
            return view(payment);
        }
        requireTransition(payment, PaymentStatus.FAILED);
        if (!Objects.equals(providerCode, payment.getChannel())) {
            throw new BizException("PAYMENT_PROVIDER_MISMATCH", "支付渠道不匹配");
        }
        LocalDateTime now = LocalDateTime.now();
        updateStatus(payment, PaymentStatus.PENDING, PaymentStatus.FAILED, failureCode, now);
        recordTransition(paymentNo, PaymentStatus.PENDING, PaymentStatus.FAILED, "PROVIDER_FAILURE",
                "PROVIDER:" + providerEventId);
        payment.setStatus(PaymentStatus.FAILED.name());
        payment.setFailureCode(failureCode);
        payment.setUpdatedAt(now);
        return view(payment);
    }

    private PaymentView confirm(CurrentUser user, String paymentNo, String providerCode, String idempotencyKey) {
        LockedPayment locked = lock(user, paymentNo);
        PaymentRecord payment = locked.payment();
        OrderRecord order = locked.order();
        if (PaymentStatus.from(payment.getStatus()) == PaymentStatus.CONFIRMED) {
            return view(payment);
        }
        validatePaymentConsistency(payment, order);
        requirePayableOrder(order);
        if (PaymentStatus.from(payment.getStatus()) == PaymentStatus.CREATED) {
            throw new BizException("PAYMENT_SESSION_REQUIRED", "请先发起支付");
        }
        requireTransition(payment, PaymentStatus.CONFIRMED);
        if (!providerCode.equals(payment.getChannel())) {
            throw new BizException("PAYMENT_PROVIDER_MISMATCH", "支付渠道不匹配");
        }
        if (!hasActiveSession(payment)) {
            throw new BizException("PAYMENT_SESSION_EXPIRED", "支付会话已过期，请重新发起支付");
        }

        LocalDateTime confirmedAt = LocalDateTime.now();
        updateStatus(payment, PaymentStatus.PENDING, PaymentStatus.CONFIRMED, null, confirmedAt);
        recordTransition(paymentNo, PaymentStatus.PENDING, PaymentStatus.CONFIRMED, "PROVIDER_CONFIRM", idempotencyKey);
        events.publish(EventRoutes.PAYMENT_CONFIRMED, "Payment", paymentNo,
                new PaymentConfirmedEvent(paymentNo, payment.getOrderNo(), user.id(), payment.getAmountCents(),
                        providerCode, confirmedAt));

        payment.setStatus(PaymentStatus.CONFIRMED.name());
        payment.setConfirmedAt(confirmedAt);
        payment.setUpdatedAt(confirmedAt);
        return view(payment);
    }

    private LockedPayment lock(CurrentUser user, String paymentNo) {
        requireUser(user);
        PaymentRecord snapshot = ownedPayment(user, paymentNo);
        OrderRecord order = orderMapper.lockOwned(snapshot.getOrderNo(), user.id());
        if (order == null) {
            throw new BizException("ORDER_NOT_FOUND", "Order not found");
        }
        PaymentRecord payment = paymentMapper.lockOwned(paymentNo, user.id());
        if (payment == null) {
            throw new BizException("PAYMENT_NOT_FOUND", "Payment not found");
        }
        return new LockedPayment(order, payment);
    }

    private PaymentRecord ownedPayment(CurrentUser user, String paymentNo) {
        requireUser(user);
        PaymentRecord payment = paymentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, paymentNo)
                .eq(PaymentRecord::getUserId, user.id())
                .last("limit 1"));
        if (payment == null) {
            throw new BizException("PAYMENT_NOT_FOUND", "Payment not found");
        }
        return payment;
    }

    private void updateStatus(PaymentRecord payment, PaymentStatus expected, PaymentStatus next,
                              String failureCode, LocalDateTime now) {
        int updated = jdbc.update("""
                UPDATE payment SET status=?, failure_code=?, updated_at=?,
                    confirmed_at=CASE WHEN ?='CONFIRMED' THEN ? ELSE confirmed_at END
                WHERE payment_no=? AND user_id=? AND status=?
                """, next.name(), failureCode, now, next.name(), now,
                payment.getPaymentNo(), payment.getUserId(), expected.name());
        if (updated == 0) {
            throw new BizException("PAYMENT_STATE_CHANGED", "支付状态已变化，请刷新订单");
        }
    }

    private void recordTransition(String paymentNo, PaymentStatus from, PaymentStatus to,
                                  String trigger, String idempotencyKey) {
        jdbc.update("""
                INSERT IGNORE INTO payment_transition
                    (payment_no, from_status, to_status, trigger_type, idempotency_key, trace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """, paymentNo, from == null ? null : from.name(), to.name(), trigger,
                idempotencyKey, TraceIds.current());
        meters.counter("gray.payment.transition", "from", from == null ? "NONE" : from.name(),
                "to", to.name(), "trigger", trigger).increment();
    }

    private void requireTransition(PaymentRecord payment, PaymentStatus next) {
        PaymentStatus current = PaymentStatus.from(payment.getStatus());
        if (!current.canTransitionTo(next)) {
            throw new BizException("PAYMENT_STATUS_INVALID", "当前支付状态不允许该操作");
        }
    }

    private void requirePayableOrder(OrderRecord order) {
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            if ("PAID".equals(order.getStatus())) {
                throw new BizException("PAYMENT_ALREADY_CONFIRMED", "订单已支付");
            }
            throw new BizException("ORDER_CANCELLED", "订单已取消或已过期");
        }
    }

    private void validatePaymentConsistency(PaymentRecord payment, OrderRecord order) {
        if (!Objects.equals(payment.getOrderNo(), order.getOrderNo())
                || !Objects.equals(payment.getUserId(), order.getUserId())
                || !Objects.equals(payment.getAmountCents(), order.getTotalCents())) {
            throw new BizException("PAYMENT_DATA_MISMATCH", "支付信息不一致");
        }
    }

    private boolean hasActiveSession(PaymentRecord payment) {
        return payment.getProviderSessionId() != null && !payment.getProviderSessionId().isBlank()
                && payment.getSessionExpiresAt() != null
                && payment.getSessionExpiresAt().isAfter(LocalDateTime.now());
    }

    private void validateProviderSession(ProviderCheckoutSession session) {
        if (session == null || !paymentProvider.code().equals(session.provider())
                || session.sessionId() == null || session.sessionId().isBlank()
                || session.expiresAt() == null || !session.expiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException("PAYMENT_PROVIDER_ERROR", "支付服务返回无效结果");
        }
    }

    private CheckoutSessionView checkoutView(PaymentRecord payment, ProviderCheckoutSession created) {
        String mode = created == null ? DemoPaymentProvider.INTERACTION_MODE : created.interactionMode();
        String redirect = created == null ? null : created.redirectUrl();
        return new CheckoutSessionView(payment.getChannel(), payment.getProviderSessionId(), payment.getPaymentNo(),
                payment.getStatus(), mode, redirect, payment.getSessionExpiresAt());
    }

    private void requireDemoProvider() {
        if (!DemoPaymentProvider.CODE.equals(paymentProvider.code())) {
            throw new BizException("PAYMENT_PROVIDER_UNAVAILABLE", "模拟支付未启用");
        }
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.id() == null || user.id() <= 0) {
            throw new BizException("UNAUTHORIZED", "Login required");
        }
    }

    private BizException paymentStateError(PaymentRecord payment, OrderRecord order) {
        PaymentStatus status = PaymentStatus.from(payment.getStatus());
        if (status == PaymentStatus.CANCELLED || status == PaymentStatus.EXPIRED || "CANCELLED".equals(order.getStatus())) {
            return new BizException("ORDER_CANCELLED", "订单已取消或已过期");
        }
        if (status == PaymentStatus.CONFIRMED || "PAID".equals(order.getStatus())) {
            return new BizException("PAYMENT_ALREADY_CONFIRMED", "订单已支付");
        }
        return new BizException("ORDER_STATUS_INVALID", "订单当前无法支付");
    }

    private PaymentView view(PaymentRecord payment) {
        return new PaymentView(payment.getPaymentNo(), payment.getOrderNo(), payment.getAmountCents(),
                payment.getChannel(), payment.getStatus(), payment.getSessionExpiresAt(), payment.getConfirmedAt());
    }

    private record LockedPayment(OrderRecord order, PaymentRecord payment) {
    }
}
