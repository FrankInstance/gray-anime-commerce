package com.gray.anime.payment.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.payment.domain.*;
import com.gray.anime.payment.infrastructure.client.InventoryClient;
import com.gray.anime.payment.infrastructure.mapper.*;
import com.gray.anime.payment.interfaces.dto.PaymentView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentApplicationService {
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final AppUserMapper userMapper;
    private final PointsLedgerMapper pointsLedgerMapper;
    private final OutboxEventMapper outboxMapper;
    private final InventoryClient inventoryClient;

    public PaymentApplicationService(PaymentMapper paymentMapper, OrderMapper orderMapper, OrderItemMapper itemMapper,
                                     AppUserMapper userMapper, PointsLedgerMapper pointsLedgerMapper, OutboxEventMapper outboxMapper, InventoryClient inventoryClient) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.pointsLedgerMapper = pointsLedgerMapper;
        this.outboxMapper = outboxMapper;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public PaymentView mockConfirm(String paymentNo) {
        PaymentRecord payment = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getPaymentNo, paymentNo));
        if (payment == null) {
            throw new BizException("PAYMENT_NOT_FOUND", "Payment not found");
        }
        if ("CONFIRMED".equals(payment.getStatus())) {
            return view(payment);
        }
        if ("CANCELLED".equals(payment.getStatus())) {
            throw new BizException("ORDER_CANCELLED", "Order has been cancelled");
        }

        OrderRecord order = orderMapper.selectOne(new LambdaQueryWrapper<OrderRecord>().eq(OrderRecord::getOrderNo, payment.getOrderNo()));
        if (order == null) {
            throw new BizException("ORDER_NOT_FOUND", "Order not found");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            cancelPayment(payment);
            throw new BizException("ORDER_CANCELLED", "Order has been cancelled");
        }

        boolean newlyPaid = false;
        if ("PENDING_PAYMENT".equals(order.getStatus())) {
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<OrderRecord>()
                    .eq(OrderRecord::getOrderNo, order.getOrderNo())
                    .eq(OrderRecord::getStatus, "PENDING_PAYMENT")
                    .set(OrderRecord::getStatus, "PAID")
                    .set(OrderRecord::getUpdatedAt, LocalDateTime.now()));
            if (updated == 0) {
                OrderRecord latest = orderMapper.selectOne(new LambdaQueryWrapper<OrderRecord>().eq(OrderRecord::getOrderNo, payment.getOrderNo()));
                if (latest == null || "CANCELLED".equals(latest.getStatus())) {
                    cancelPayment(payment);
                    throw new BizException("ORDER_CANCELLED", "Order has been cancelled");
                }
                order = latest;
            } else {
                order.setStatus("PAID");
                order.setUpdatedAt(LocalDateTime.now());
                newlyPaid = true;
            }
        } else if (!"PAID".equals(order.getStatus())) {
            throw new BizException("ORDER_STATUS_INVALID", "Order cannot be paid");
        }

        payment.setStatus("CONFIRMED");
        payment.setConfirmedAt(LocalDateTime.now());
        paymentMapper.updateById(payment);

        if (newlyPaid) {
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
        outbox("Payment", paymentNo, "PaymentConfirmed", "{\"paymentNo\":\"" + paymentNo + "\",\"orderNo\":\"" + payment.getOrderNo() + "\"}");
        return view(payment);
    }

    private void cancelPayment(PaymentRecord payment) {
        if (!"CANCELLED".equals(payment.getStatus())) {
            payment.setStatus("CANCELLED");
            paymentMapper.updateById(payment);
        }
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
