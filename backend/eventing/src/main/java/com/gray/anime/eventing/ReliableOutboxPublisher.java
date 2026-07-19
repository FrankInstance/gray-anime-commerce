package com.gray.anime.eventing;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ReliableOutboxPublisher {
    private static final int MAX_ATTEMPTS = 10;

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final MeterRegistry meters;
    private final String producer;
    private final AtomicInteger backlog = new AtomicInteger();
    private final AtomicInteger dead = new AtomicInteger();

    public ReliableOutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate,
                                   MeterRegistry meters, String producer) {
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.meters = meters;
        this.producer = producer;
        this.rabbitTemplate.setMandatory(true);
        meters.gauge("gray.outbox.backlog", List.of(io.micrometer.core.instrument.Tag.of("producer", producer)),
                backlog);
        meters.gauge("gray.outbox.dead", List.of(io.micrometer.core.instrument.Tag.of("producer", producer)), dead);
    }

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:1000}")
    public void publishAvailable() {
        recoverStaleClaims();
        refreshGauges();
        for (PendingEvent event : availableEvents()) {
            if (!claim(event.id())) {
                continue;
            }
            try {
                publish(event);
                jdbc.update("UPDATE outbox_event SET status='PUBLISHED', published_at=NOW(), last_error=NULL "
                        + "WHERE id=? AND status='PUBLISHING'", event.id());
                meters.counter("gray.outbox.publish", "producer", producer, "result", "success").increment();
            } catch (Exception exception) {
                fail(event, exception);
                meters.counter("gray.outbox.publish", "producer", producer, "result", "failure").increment();
            }
        }
        refreshGauges();
    }

    private List<PendingEvent> availableEvents() {
        return jdbc.query("""
                SELECT id, event_id, routing_key, payload, retry_count
                FROM outbox_event
                WHERE producer=? AND status IN ('NEW','RETRY') AND available_at <= NOW()
                ORDER BY id LIMIT 20
                """, (ResultSet rs, int row) -> new PendingEvent(rs.getLong("id"), rs.getString("event_id"),
                rs.getString("routing_key"), rs.getString("payload"), rs.getInt("retry_count")), producer);
    }

    private boolean claim(long id) {
        return jdbc.update("UPDATE outbox_event SET status='PUBLISHING', "
                + "available_at=DATE_ADD(NOW(), INTERVAL 30 SECOND) "
                + "WHERE id=? AND status IN ('NEW','RETRY') AND available_at <= NOW()", id) == 1;
    }

    private void recoverStaleClaims() {
        jdbc.update("UPDATE outbox_event SET status='RETRY', last_error='PUBLISH_TIMEOUT' "
                + "WHERE producer=? AND status='PUBLISHING' AND available_at <= NOW()", producer);
    }

    private void publish(PendingEvent event) throws Exception {
        Message message = MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.eventId())
                .build();
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.send(EventRoutes.EXCHANGE, event.routingKey(), message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException(confirm.getReason() == null ? "event was not routed" : confirm.getReason());
        }
    }

    private void fail(PendingEvent event, Exception exception) {
        int attempts = event.retryCount() + 1;
        String status = attempts >= MAX_ATTEMPTS ? "DEAD" : "RETRY";
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        jdbc.update("""
                UPDATE outbox_event
                SET status=?, retry_count=?, available_at=DATE_ADD(NOW(), INTERVAL ? SECOND), last_error=?
                WHERE id=? AND status='PUBLISHING'
                """, status, attempts, delaySeconds, exception.getClass().getSimpleName(), event.id());
    }

    private void refreshGauges() {
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE producer=? AND status IN ('NEW','RETRY','PUBLISHING')",
                Integer.class, producer);
        Integer deadCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE producer=? AND status='DEAD'",
                Integer.class, producer);
        backlog.set(pending == null ? 0 : pending);
        dead.set(deadCount == null ? 0 : deadCount);
    }

    private record PendingEvent(long id, String eventId, String routingKey, String payload, int retryCount) {
    }
}
