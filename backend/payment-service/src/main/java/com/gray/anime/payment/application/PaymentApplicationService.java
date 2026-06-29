package com.gray.anime.payment.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final OutboxEventMapper outboxMapper;
    private final InventoryClient inventoryClient;

    public PaymentApplicationService(PaymentMapper paymentMapper, OrderMapper orderMapper, OrderItemMapper itemMapper,
                                     AppUserMapper userMapper, OutboxEventMapper outboxMapper, InventoryClient inventoryClient) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
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
        payment.setStatus("CONFIRMED");
        payment.setConfirmedAt(LocalDateTime.now());
        paymentMapper.updateById(payment);

        OrderRecord order = orderMapper.selectOne(new LambdaQueryWrapper<OrderRecord>().eq(OrderRecord::getOrderNo, payment.getOrderNo()));
        if (order != null && !"PAID".equals(order.getStatus())) {
            order.setStatus("PAID");
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
            if ("PRODUCT".equals(order.getOrderType())) {
                confirmReservations(order.getId());
            }
            if ("VIP".equals(order.getOrderType())) {
                activateVip(order.getUserId());
            }
        }
        outbox("Payment", paymentNo, "PaymentConfirmed", "{\"paymentNo\":\"" + paymentNo + "\",\"orderNo\":\"" + payment.getOrderNo() + "\"}");
        return view(payment);
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
