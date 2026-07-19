package com.gray.anime.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gray.anime.common.api.TraceIds;
import com.gray.anime.eventing.*;
import com.gray.anime.order.domain.*;
import com.gray.anime.order.infrastructure.mapper.OrderItemMapper;
import com.gray.anime.order.infrastructure.mapper.OrderMapper;
import com.gray.anime.order.infrastructure.mapper.PaymentMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderLifecycleService {
    private static final int EXPIRE_BATCH_SIZE = 50;
    private static final String PAYMENT_CONSUMER = "order-payment-confirmed-v1";
    private static final String FULFILLMENT_CONSUMER = "order-fulfillment-result-v1";

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final PaymentMapper paymentMapper;
    private final OrderExpirationPolicy expirationPolicy;
    private final DomainEventPublisher events;
    private final InboxDeduplicator inbox;
    private final ObjectMapper objectMapper;

    public OrderLifecycleService(OrderMapper orderMapper, OrderItemMapper itemMapper, PaymentMapper paymentMapper,
                                 OrderExpirationPolicy expirationPolicy, DomainEventPublisher events,
                                 InboxDeduplicator inbox, ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.paymentMapper = paymentMapper;
        this.expirationPolicy = expirationPolicy;
        this.events = events;
        this.inbox = inbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int cancelExpiredPendingOrders() {
        LocalDateTime cutoff = expirationPolicy.cutoff(LocalDateTime.now());
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

    @Transactional
    public boolean cancelPendingOrder(OrderRecord snapshot, String reason) {
        OrderRecord order = orderMapper.lockOwned(snapshot.getOrderNo(), snapshot.getUserId());
        if (order == null || !OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            return false;
        }
        PaymentRecord payment = order.getPaymentNo() == null ? null : paymentMapper.lockByPaymentNo(order.getPaymentNo());
        if (payment != null && "CONFIRMED".equals(payment.getStatus())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        String paymentStatus = "PAYMENT_TIMEOUT".equals(reason) ? "EXPIRED" : "CANCELLED";
        if (payment != null) {
            String currentPaymentStatus = payment.getStatus();
            if (List.of("CREATED", "PENDING", "FAILED").contains(currentPaymentStatus)) {
                int paymentUpdated = paymentMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getId, payment.getId())
                        .eq(PaymentRecord::getStatus, currentPaymentStatus)
                        .set(PaymentRecord::getStatus, paymentStatus)
                        .set(PaymentRecord::getFailureCode, reason)
                        .set(PaymentRecord::getUpdatedAt, now));
                if (paymentUpdated == 0) {
                    return false;
                }
                paymentMapper.insertTransition(payment.getPaymentNo(), currentPaymentStatus, paymentStatus,
                        reason, reason + ":" + payment.getPaymentNo(), TraceIds.current());
            } else if (!List.of("CANCELLED", "EXPIRED").contains(currentPaymentStatus)) {
                return false;
            }
        }

        boolean product = "PRODUCT".equals(order.getOrderType());
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<OrderRecord>()
                .eq(OrderRecord::getId, order.getId())
                .eq(OrderRecord::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .set(OrderRecord::getStatus, OrderStatus.CANCELLED.name())
                .set(OrderRecord::getCancelReason, reason)
                .set(OrderRecord::getCancelledAt, now)
                .set(OrderRecord::getFulfillmentStatus,
                        product ? FulfillmentStatus.PENDING.name() : FulfillmentStatus.NOT_REQUIRED.name())
                .set(OrderRecord::getUpdatedAt, now));
        if (updated == 0) {
            return false;
        }

        order.setStatus(OrderStatus.CANCELLED.name());
        order.setCancelReason(reason);
        order.setCancelledAt(now);
        order.setFulfillmentStatus(product ? FulfillmentStatus.PENDING.name() : FulfillmentStatus.NOT_REQUIRED.name());
        order.setUpdatedAt(now);
        if (product) {
            events.publish(EventRoutes.ORDER_CANCELLED_PRODUCT, "Order", order.getOrderNo(),
                    new OrderCancelledEvent(order.getOrderNo(), order.getId(), reason, reservationNos(order.getId())));
        }
        return true;
    }

    @RabbitListener(queues = EventRoutes.ORDER_PAYMENT_QUEUE)
    @Transactional
    public void onPaymentConfirmed(byte[] body) {
        DomainEventEnvelope envelope = read(body);
        if (!EventRoutes.PAYMENT_CONFIRMED.equals(envelope.eventType())
                || !inbox.claim(PAYMENT_CONSUMER, envelope.eventId())) {
            return;
        }
        PaymentConfirmedEvent event = objectMapper.convertValue(envelope.payload(), PaymentConfirmedEvent.class);
        OrderRecord order = orderMapper.lockOwned(event.orderNo(), event.userId());
        if (order == null) {
            throw new IllegalStateException("Payment event references a missing order");
        }
        PaymentRecord payment = paymentMapper.lockByPaymentNo(event.paymentNo());
        if (payment == null || !"CONFIRMED".equals(payment.getStatus())
                || !event.amountCents().equals(order.getTotalCents())) {
            throw new IllegalStateException("Payment event does not match the order");
        }
        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            return;
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new IllegalStateException("Confirmed payment cannot transition the order");
        }

        LocalDateTime paidAt = event.confirmedAt();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<OrderRecord>()
                .eq(OrderRecord::getId, order.getId())
                .eq(OrderRecord::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .set(OrderRecord::getStatus, OrderStatus.PAID.name())
                .set(OrderRecord::getPaidAt, paidAt)
                .set(OrderRecord::getFulfillmentStatus, FulfillmentStatus.PENDING.name())
                .set(OrderRecord::getUpdatedAt, paidAt));
        if (updated == 0) {
            throw new IllegalStateException("Order state changed while applying payment");
        }

        OrderPaidEvent paid = new OrderPaidEvent(order.getId(), order.getOrderNo(), order.getUserId(),
                order.getOrderType(), order.getTotalCents(), order.getTotalPoints(),
                reservationNos(order.getId()), paidAt);
        events.publish(routeFor(order.getOrderType()), "Order", order.getOrderNo(), paid);
    }

    @RabbitListener(queues = EventRoutes.ORDER_FULFILLMENT_QUEUE)
    @Transactional
    public void onFulfillmentResult(byte[] body) {
        DomainEventEnvelope envelope = read(body);
        if (!inbox.claim(FULFILLMENT_CONSUMER, envelope.eventId())) {
            return;
        }
        FulfillmentResultEvent event = objectMapper.convertValue(envelope.payload(), FulfillmentResultEvent.class);
        String status = EventRoutes.FULFILLMENT_COMPLETED.equals(envelope.eventType())
                ? FulfillmentStatus.COMPLETED.name() : FulfillmentStatus.FAILED.name();
        orderMapper.update(null, new LambdaUpdateWrapper<OrderRecord>()
                .eq(OrderRecord::getOrderNo, event.orderNo())
                .eq(OrderRecord::getFulfillmentStatus, FulfillmentStatus.PENDING.name())
                .set(OrderRecord::getFulfillmentStatus, status)
                .set(OrderRecord::getUpdatedAt, event.occurredAt()));
    }

    private String routeFor(String orderType) {
        return switch (orderType) {
            case "PRODUCT" -> EventRoutes.ORDER_PAID_PRODUCT;
            case "VIP" -> EventRoutes.ORDER_PAID_VIP;
            case "POINTS" -> EventRoutes.ORDER_PAID_POINTS;
            default -> throw new IllegalStateException("Unsupported paid order type: " + orderType);
        };
    }

    private List<String> reservationNos(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)).stream()
                .map(OrderItem::getReservationNo)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private DomainEventEnvelope read(byte[] body) {
        try {
            return objectMapper.readValue(body, DomainEventEnvelope.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid domain event", exception);
        }
    }
}
