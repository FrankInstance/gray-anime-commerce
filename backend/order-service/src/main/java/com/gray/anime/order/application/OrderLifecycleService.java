package com.gray.anime.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gray.anime.order.domain.OrderItem;
import com.gray.anime.order.domain.OrderRecord;
import com.gray.anime.order.domain.OrderStatus;
import com.gray.anime.order.domain.OutboxEvent;
import com.gray.anime.order.domain.PaymentRecord;
import com.gray.anime.order.infrastructure.client.InventoryClient;
import com.gray.anime.order.infrastructure.mapper.OrderItemMapper;
import com.gray.anime.order.infrastructure.mapper.OrderMapper;
import com.gray.anime.order.infrastructure.mapper.OutboxEventMapper;
import com.gray.anime.order.infrastructure.mapper.PaymentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderLifecycleService {
    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(10);
    private static final int EXPIRE_BATCH_SIZE = 50;

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final PaymentMapper paymentMapper;
    private final OutboxEventMapper outboxMapper;
    private final InventoryClient inventoryClient;

    public OrderLifecycleService(OrderMapper orderMapper, OrderItemMapper itemMapper, PaymentMapper paymentMapper,
                                 OutboxEventMapper outboxMapper, InventoryClient inventoryClient) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.paymentMapper = paymentMapper;
        this.outboxMapper = outboxMapper;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public int cancelExpiredPendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(PAYMENT_TIMEOUT);
        List<OrderRecord> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderRecord>()
                .eq(OrderRecord::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .le(OrderRecord::getCreatedAt, cutoff)
                .orderByAsc(OrderRecord::getId)
                .last("limit " + EXPIRE_BATCH_SIZE));

        int cancelled = 0;
        for (OrderRecord order : orders) {
            if (cancelPendingOrder(order, "PAYMENT_TIMEOUT")) {
                cancelled++;
            }
        }
        return cancelled;
    }

    public boolean cancelPendingOrder(OrderRecord order, String reason) {
        return transition(order, OrderStatus.CANCELLED, reason);
    }

    private boolean transition(OrderRecord order, OrderStatus nextStatus, String reason) {
        OrderStatus currentStatus = OrderStatus.from(order.getStatus());
        if (!currentStatus.canTransitionTo(nextStatus)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<OrderRecord>()
                .eq(OrderRecord::getId, order.getId())
                .eq(OrderRecord::getStatus, currentStatus.name())
                .set(OrderRecord::getStatus, nextStatus.name())
                .set(OrderRecord::getUpdatedAt, now));
        if (updated == 0) {
            return false;
        }

        order.setStatus(nextStatus.name());
        order.setUpdatedAt(now);
        if (nextStatus == OrderStatus.CANCELLED) {
            cancelPayment(order);
            releaseReservations(order.getId());
            outbox("Order", order.getOrderNo(), "OrderCancelled", "{\"orderNo\":\"" + order.getOrderNo() + "\",\"reason\":\"" + reason + "\"}");
        }
        return true;
    }

    private void cancelPayment(OrderRecord order) {
        if (order.getPaymentNo() == null || order.getPaymentNo().isBlank()) {
            return;
        }
        paymentMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, order.getPaymentNo())
                .eq(PaymentRecord::getStatus, "PENDING")
                .set(PaymentRecord::getStatus, "CANCELLED"));
    }

    private void releaseReservations(Long orderId) {
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            if (item.getReservationNo() != null && !item.getReservationNo().isBlank()) {
                inventoryClient.release(item.getReservationNo());
            }
        }
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
}
