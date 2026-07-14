package com.gray.anime.payment.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.payment.application.provider.PaymentCheckout;
import com.gray.anime.payment.application.provider.PaymentProvider;
import com.gray.anime.payment.application.provider.ProviderCheckoutSession;
import com.gray.anime.payment.domain.*;
import com.gray.anime.payment.infrastructure.client.InventoryClient;
import com.gray.anime.payment.infrastructure.mapper.*;
import com.gray.anime.payment.infrastructure.provider.DemoPaymentProvider;
import com.gray.anime.payment.interfaces.dto.CheckoutSessionRequest;
import com.gray.anime.payment.interfaces.dto.CheckoutSessionView;
import com.gray.anime.payment.interfaces.dto.PaymentView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PaymentApplicationService {
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final AppUserMapper userMapper;
    private final PointsLedgerMapper pointsLedgerMapper;
    private final OutboxEventMapper outboxMapper;
    private final InventoryClient inventoryClient;
    private final PaymentProvider paymentProvider;

    public PaymentApplicationService(PaymentMapper paymentMapper, OrderMapper orderMapper, OrderItemMapper itemMapper,
                                     AppUserMapper userMapper, PointsLedgerMapper pointsLedgerMapper, OutboxEventMapper outboxMapper,
                                     InventoryClient inventoryClient, PaymentProvider paymentProvider) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.pointsLedgerMapper = pointsLedgerMapper;
        this.outboxMapper = outboxMapper;
        this.inventoryClient = inventoryClient;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public CheckoutSessionView createCheckoutSession(CurrentUser user, CheckoutSessionRequest request) {
        PaymentRecord payment = ownedPayment(user, request.paymentNo());
        if ("CONFIRMED".equals(payment.getStatus())) {
            throw new BizException("PAYMENT_ALREADY_CONFIRMED", "订单已支付");
        }
        OrderRecord order = relatedOrder(payment, user.id());
        validatePaymentConsistency(payment, order);
        if (!"PENDING".equals(payment.getStatus()) || !"PENDING_PAYMENT".equals(order.getStatus())) {
            throw paymentStateError(payment, order);
        }

        ProviderCheckoutSession session = paymentProvider.createSession(
                new PaymentCheckout(payment.getPaymentNo(), payment.getOrderNo(), payment.getAmountCents())
        );
        validateProviderSession(payment, session);

        int assigned = paymentMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, payment.getPaymentNo())
                .eq(PaymentRecord::getUserId, user.id())
                .eq(PaymentRecord::getStatus, "PENDING")
                .set(PaymentRecord::getChannel, paymentProvider.code()));
        if (assigned == 0) {
            throw new BizException("PAYMENT_STATE_CHANGED", "支付状态已变化，请刷新订单");
        }
        payment.setChannel(paymentProvider.code());
        return new CheckoutSessionView(
                session.provider(),
                session.sessionId(),
                payment.getPaymentNo(),
                session.interactionMode(),
                session.redirectUrl(),
                session.expiresAt()
        );
    }

    @Transactional
    public PaymentView confirmDemo(CurrentUser user, String paymentNo) {
        requireDemoProvider();
        PaymentRecord payment = ownedPayment(user, paymentNo);
        if (!"CONFIRMED".equals(payment.getStatus()) && !DemoPaymentProvider.CODE.equals(payment.getChannel())) {
            throw new BizException("PAYMENT_SESSION_REQUIRED", "请先创建支付会话");
        }
        return settlePayment(payment, user.id(), DemoPaymentProvider.CODE);
    }

    @Transactional
    public PaymentView confirmLegacyMock(CurrentUser user, String paymentNo) {
        requireDemoProvider();
        PaymentRecord payment = ownedPayment(user, paymentNo);
        if (!"CONFIRMED".equals(payment.getStatus()) && !DemoPaymentProvider.CODE.equals(payment.getChannel())) {
            int assigned = paymentMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                    .eq(PaymentRecord::getPaymentNo, payment.getPaymentNo())
                    .eq(PaymentRecord::getUserId, user.id())
                    .eq(PaymentRecord::getStatus, "PENDING")
                    .set(PaymentRecord::getChannel, DemoPaymentProvider.CODE));
            if (assigned == 0) {
                throw new BizException("PAYMENT_STATE_CHANGED", "支付状态已变化，请刷新订单");
            }
            payment.setChannel(DemoPaymentProvider.CODE);
        }
        return settlePayment(payment, user.id(), DemoPaymentProvider.CODE);
    }

    private PaymentView settlePayment(PaymentRecord payment, Long userId, String providerCode) {
        if ("CONFIRMED".equals(payment.getStatus())) {
            return view(payment);
        }
        OrderRecord order = relatedOrder(payment, userId);
        validatePaymentConsistency(payment, order);
        if (!"PENDING".equals(payment.getStatus())) {
            throw paymentStateError(payment, order);
        }
        if (!providerCode.equals(payment.getChannel())) {
            throw new BizException("PAYMENT_PROVIDER_MISMATCH", "支付渠道不匹配");
        }

        LocalDateTime confirmedAt = LocalDateTime.now();
        int confirmed = paymentMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, payment.getPaymentNo())
                .eq(PaymentRecord::getUserId, userId)
                .eq(PaymentRecord::getStatus, "PENDING")
                .eq(PaymentRecord::getChannel, providerCode)
                .set(PaymentRecord::getStatus, "CONFIRMED")
                .set(PaymentRecord::getConfirmedAt, confirmedAt));
        if (confirmed == 0) {
            PaymentRecord latest = ownedPayment(userId, payment.getPaymentNo());
            if ("CONFIRMED".equals(latest.getStatus())) {
                return view(latest);
            }
            throw paymentStateError(latest, relatedOrder(latest, userId));
        }

        boolean newlyPaid = false;
        if ("PENDING_PAYMENT".equals(order.getStatus())) {
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<OrderRecord>()
                    .eq(OrderRecord::getOrderNo, order.getOrderNo())
                    .eq(OrderRecord::getUserId, userId)
                    .eq(OrderRecord::getStatus, "PENDING_PAYMENT")
                    .set(OrderRecord::getStatus, "PAID")
                    .set(OrderRecord::getUpdatedAt, confirmedAt));
            if (updated == 0) {
                OrderRecord latest = relatedOrder(payment, userId);
                if (!"PAID".equals(latest.getStatus())) {
                    throw paymentStateError(payment, latest);
                }
                order = latest;
            } else {
                order.setStatus("PAID");
                order.setUpdatedAt(confirmedAt);
                newlyPaid = true;
            }
        } else if (!"PAID".equals(order.getStatus())) {
            throw paymentStateError(payment, order);
        }

        payment.setStatus("CONFIRMED");
        payment.setConfirmedAt(confirmedAt);
        if (newlyPaid) {
            applyPaidOrderBenefits(order);
        }
        outbox(
                "Payment",
                payment.getPaymentNo(),
                "PaymentConfirmed",
                "{\"paymentNo\":\"" + payment.getPaymentNo() + "\",\"orderNo\":\"" + payment.getOrderNo()
                        + "\",\"provider\":\"" + providerCode + "\"}"
        );
        return view(payment);
    }

    private void applyPaidOrderBenefits(OrderRecord order) {
        if ("PRODUCT".equals(order.getOrderType())) {
            confirmReservations(order.getId());
        }
        if ("VIP".equals(order.getOrderType())) {
            activateVip(order.getUserId());
        }
        if ("POINTS".equals(order.getOrderType())) {
            rechargePoints(order.getUserId(), order.getTotalPoints(), order.getOrderNo());
        }
    }

    private PaymentRecord ownedPayment(CurrentUser user, String paymentNo) {
        if (user == null || user.id() == null || user.id() <= 0) {
            throw new BizException("UNAUTHORIZED", "Login required");
        }
        return ownedPayment(user.id(), paymentNo);
    }

    private PaymentRecord ownedPayment(Long userId, String paymentNo) {
        PaymentRecord payment = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, paymentNo)
                .eq(PaymentRecord::getUserId, userId)
                .last("limit 1"));
        if (payment == null || !Objects.equals(payment.getUserId(), userId)) {
            throw new BizException("PAYMENT_NOT_FOUND", "Payment not found");
        }
        return payment;
    }

    private OrderRecord relatedOrder(PaymentRecord payment, Long userId) {
        OrderRecord order = orderMapper.selectOne(new LambdaQueryWrapper<OrderRecord>()
                .eq(OrderRecord::getOrderNo, payment.getOrderNo())
                .eq(OrderRecord::getUserId, userId)
                .last("limit 1"));
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException("ORDER_NOT_FOUND", "Order not found");
        }
        return order;
    }

    private void validatePaymentConsistency(PaymentRecord payment, OrderRecord order) {
        if (!Objects.equals(payment.getOrderNo(), order.getOrderNo())
                || !Objects.equals(payment.getUserId(), order.getUserId())
                || !Objects.equals(payment.getAmountCents(), order.getTotalCents())) {
            throw new BizException("PAYMENT_DATA_MISMATCH", "支付信息不一致");
        }
    }

    private void validateProviderSession(PaymentRecord payment, ProviderCheckoutSession session) {
        if (session == null
                || !paymentProvider.code().equals(session.provider())
                || session.sessionId() == null
                || session.sessionId().isBlank()) {
            throw new BizException("PAYMENT_PROVIDER_ERROR", "支付服务返回无效结果");
        }
    }

    private void requireDemoProvider() {
        if (!DemoPaymentProvider.CODE.equals(paymentProvider.code())) {
            throw new BizException("PAYMENT_PROVIDER_UNAVAILABLE", "模拟支付未启用");
        }
    }

    private BizException paymentStateError(PaymentRecord payment, OrderRecord order) {
        if ("CANCELLED".equals(payment.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            return new BizException("ORDER_CANCELLED", "Order has been cancelled");
        }
        if ("CONFIRMED".equals(payment.getStatus()) || "PAID".equals(order.getStatus())) {
            return new BizException("PAYMENT_ALREADY_CONFIRMED", "订单已支付");
        }
        return new BizException("ORDER_STATUS_INVALID", "Order cannot be paid");
    }

    private void confirmReservations(Long orderId) {
        List<OrderItemRecord> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItemRecord>().eq(OrderItemRecord::getOrderId, orderId));
        for (OrderItemRecord item : items) {
            if (item.getReservationNo() != null && !item.getReservationNo().isBlank()) {
                inventoryClient.confirm(item.getReservationNo());
            }
        }
    }

    private void activateVip(Long userId) {
        AppUserRecord user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        LocalDateTime base = user.getVipUntil() == null || user.getVipUntil().isBefore(LocalDateTime.now())
                ? LocalDateTime.now()
                : user.getVipUntil();
        user.setVipUntil(base.plusMonths(1));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private void rechargePoints(Long userId, Integer points, String orderNo) {
        if (points == null || points <= 0) {
            return;
        }
        AppUserRecord user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setPoints((user.getPoints() == null ? 0 : user.getPoints()) + points);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        PointsLedgerRecord ledger = new PointsLedgerRecord();
        ledger.setUserId(userId);
        ledger.setAmount(points);
        ledger.setReason("POINTS_RECHARGE");
        ledger.setBizKey("POINTS_ORDER:" + orderNo);
        ledger.setCreatedAt(LocalDateTime.now());
        pointsLedgerMapper.insert(ledger);
    }

    private void outbox(String aggregateType, String aggregateId, String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setStatus("NEW");
        event.setRetryCount(0);
        event.setCreatedAt(LocalDateTime.now());
        outboxMapper.insert(event);
    }

    private PaymentView view(PaymentRecord payment) {
        return new PaymentView(payment.getPaymentNo(), payment.getOrderNo(), payment.getAmountCents(), payment.getChannel(), payment.getStatus(), payment.getConfirmedAt());
    }
}
