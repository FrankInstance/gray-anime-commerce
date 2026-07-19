package com.gray.anime.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gray.anime.eventing.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class InventoryFulfillmentEventListener {
    private static final String CONSUMER = "inventory-order-fulfillment-v1";
    private static final String DEAD_CONSUMER = "inventory-order-fulfillment-dead-v1";

    private final InventoryApplicationService inventory;
    private final InboxDeduplicator inbox;
    private final DomainEventPublisher events;
    private final ObjectMapper objectMapper;

    public InventoryFulfillmentEventListener(InventoryApplicationService inventory, InboxDeduplicator inbox,
                                             DomainEventPublisher events, ObjectMapper objectMapper) {
        this.inventory = inventory;
        this.inbox = inbox;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = EventRoutes.INVENTORY_FULFILLMENT_QUEUE)
    @Transactional
    public void fulfill(byte[] body) {
        DomainEventEnvelope envelope = read(body);
        if (!inbox.claim(CONSUMER, envelope.eventId())) {
            return;
        }
        String orderNo;
        if (EventRoutes.ORDER_PAID_PRODUCT.equals(envelope.eventType())) {
            OrderPaidEvent order = objectMapper.convertValue(envelope.payload(), OrderPaidEvent.class);
            order.reservationNos().forEach(inventory::confirm);
            orderNo = order.orderNo();
        } else if (EventRoutes.ORDER_CANCELLED_PRODUCT.equals(envelope.eventType())) {
            OrderCancelledEvent order = objectMapper.convertValue(envelope.payload(), OrderCancelledEvent.class);
            order.reservationNos().forEach(inventory::release);
            orderNo = order.orderNo();
        } else {
            throw new IllegalArgumentException("Unsupported inventory fulfillment event");
        }
        events.publish(EventRoutes.FULFILLMENT_COMPLETED, "Order", orderNo,
                new FulfillmentResultEvent(orderNo, "inventory-service", null, LocalDateTime.now()));
    }

    @RabbitListener(queues = EventRoutes.INVENTORY_FULFILLMENT_QUEUE + ".dead")
    @Transactional
    public void dead(byte[] body) {
        DomainEventEnvelope envelope = read(body);
        if (!inbox.claim(DEAD_CONSUMER, envelope.eventId())) {
            return;
        }
        String orderNo = EventRoutes.ORDER_PAID_PRODUCT.equals(envelope.eventType())
                ? objectMapper.convertValue(envelope.payload(), OrderPaidEvent.class).orderNo()
                : objectMapper.convertValue(envelope.payload(), OrderCancelledEvent.class).orderNo();
        events.publish(EventRoutes.FULFILLMENT_FAILED, "Order", orderNo,
                new FulfillmentResultEvent(orderNo, "inventory-service", "RETRY_EXHAUSTED", LocalDateTime.now()));
    }

    private DomainEventEnvelope read(byte[] body) {
        try {
            return objectMapper.readValue(body, DomainEventEnvelope.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid domain event", exception);
        }
    }
}
