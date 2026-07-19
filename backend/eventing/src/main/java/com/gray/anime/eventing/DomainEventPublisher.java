package com.gray.anime.eventing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public class DomainEventPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final String producer;

    public DomainEventPublisher(JdbcTemplate jdbc, ObjectMapper objectMapper, String producer) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    public String publish(String routingKey, String aggregateType, String aggregateId, Object payload) {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        DomainEventEnvelope envelope = new DomainEventEnvelope(eventId, routingKey, aggregateType, aggregateId,
                1, now, objectMapper.valueToTree(payload));
        jdbc.update("""
                INSERT INTO outbox_event
                    (event_id, producer, aggregate_type, aggregate_id, event_type, routing_key, payload,
                     status, retry_count, available_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'NEW', 0, ?, ?)
                """, eventId, producer, aggregateType, aggregateId, routingKey, routingKey,
                json(envelope), Timestamp.from(now), Timestamp.from(now));
        return eventId;
    }

    private String json(DomainEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Event payload is not serializable", exception);
        }
    }
}
