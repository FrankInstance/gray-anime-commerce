package com.gray.anime.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gray.anime.eventing.*;
import com.gray.anime.user.domain.PointsLedger;
import com.gray.anime.user.infrastructure.mapper.AppUserMapper;
import com.gray.anime.user.infrastructure.mapper.PointsLedgerMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class OrderBenefitEventListener {
    private static final String CONSUMER = "user-order-benefit-v1";
    private static final String DEAD_CONSUMER = "user-order-benefit-dead-v1";

    private final AppUserMapper users;
    private final PointsLedgerMapper ledger;
    private final JdbcTemplate jdbc;
    private final InboxDeduplicator inbox;
    private final DomainEventPublisher events;
    private final ObjectMapper objectMapper;

    public OrderBenefitEventListener(AppUserMapper users, PointsLedgerMapper ledger, JdbcTemplate jdbc,
                                     InboxDeduplicator inbox, DomainEventPublisher events, ObjectMapper objectMapper) {
        this.users = users;
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = EventRoutes.USER_FULFILLMENT_QUEUE)
    @Transactional
    public void fulfill(byte[] body) {
        DomainEventEnvelope envelope = read(body);
        if (!inbox.claim(CONSUMER, envelope.eventId())) {
            return;
        }
        OrderPaidEvent order = objectMapper.convertValue(envelope.payload(), OrderPaidEvent.class);
        if (users.selectById(order.userId()) == null) {
            throw new IllegalStateException("Paid order references a missing user");
        }
        if (EventRoutes.ORDER_PAID_POINTS.equals(envelope.eventType())) {
            applyPoints(order);
        } else if (EventRoutes.ORDER_PAID_VIP.equals(envelope.eventType())) {
            applyVip(order);
        } else {
            throw new IllegalArgumentException("Unsupported user fulfillment event");
        }
        events.publish(EventRoutes.FULFILLMENT_COMPLETED, "Order", order.orderNo(),
                new FulfillmentResultEvent(order.orderNo(), "user-service", null, LocalDateTime.now()));
    }

    @RabbitListener(queues = EventRoutes.USER_FULFILLMENT_QUEUE + ".dead")
    @Transactional
    public void dead(byte[] body) {
        DomainEventEnvelope envelope = read(body);
        if (!inbox.claim(DEAD_CONSUMER, envelope.eventId())) {
            return;
        }
        OrderPaidEvent order = objectMapper.convertValue(envelope.payload(), OrderPaidEvent.class);
        events.publish(EventRoutes.FULFILLMENT_FAILED, "Order", order.orderNo(),
                new FulfillmentResultEvent(order.orderNo(), "user-service", "RETRY_EXHAUSTED", LocalDateTime.now()));
    }

    private void applyPoints(OrderPaidEvent order) {
        int points = order.totalPoints() == null ? 0 : order.totalPoints();
        if (points <= 0) {
            throw new IllegalStateException("Points order has no points");
        }
        jdbc.update("UPDATE app_user SET points=points + ?, updated_at=NOW() WHERE id=?", points, order.userId());
        PointsLedger entry = new PointsLedger();
        entry.setUserId(order.userId());
        entry.setAmount(points);
        entry.setReason("POINTS_RECHARGE");
        entry.setBizKey("POINTS_ORDER:" + order.orderNo());
        entry.setCreatedAt(LocalDateTime.now());
        ledger.insert(entry);
    }

    private void applyVip(OrderPaidEvent order) {
        jdbc.update("""
                UPDATE app_user
                SET vip_until=CASE
                    WHEN vip_until IS NULL OR vip_until < NOW() THEN DATE_ADD(NOW(), INTERVAL 1 MONTH)
                    ELSE DATE_ADD(vip_until, INTERVAL 1 MONTH)
                END,
                roles=CASE WHEN FIND_IN_SET('VIP', roles)=0 THEN CONCAT_WS(',', roles, 'VIP') ELSE roles END,
                updated_at=NOW()
                WHERE id=?
                """, order.userId());
    }

    private DomainEventEnvelope read(byte[] body) {
        try {
            return objectMapper.readValue(body, DomainEventEnvelope.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid domain event", exception);
        }
    }
}
