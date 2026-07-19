package com.gray.anime.eventing;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReliableOutboxPublisherTest {
    @Test
    void brokerAckMarksTheEventPublished() throws Exception {
        Fixture fixture = new Fixture(0);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(fixture.rabbit).send(eq(EventRoutes.EXCHANGE), eq(EventRoutes.PAYMENT_CONFIRMED), any(), any());

        fixture.publisher.publishAvailable();

        verify(fixture.jdbc).update(contains("status='PUBLISHED'"), eq(1L));
    }

    @Test
    void finalBrokerNackMarksTheEventDead() throws Exception {
        Fixture fixture = new Fixture(9);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(fixture.rabbit).send(eq(EventRoutes.EXCHANGE), eq(EventRoutes.PAYMENT_CONFIRMED), any(), any());

        fixture.publisher.publishAvailable();

        verify(fixture.jdbc).update(contains("SET status=?, retry_count=?"),
                eq("DEAD"), eq(10), anyLong(), eq("IllegalStateException"), eq(1L));
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
        private final ReliableOutboxPublisher publisher;

        private Fixture(int retryCount) throws Exception {
            ResultSet result = mock(ResultSet.class);
            when(result.getLong("id")).thenReturn(1L);
            when(result.getString("event_id")).thenReturn("event-1");
            when(result.getString("routing_key")).thenReturn(EventRoutes.PAYMENT_CONFIRMED);
            when(result.getString("payload")).thenReturn("{\"eventId\":\"event-1\"}");
            when(result.getInt("retry_count")).thenReturn(retryCount);
            when(jdbc.query(anyString(), any(RowMapper.class), eq("payment-service"))).thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(result, 0));
            });
            when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("payment-service"))).thenReturn(0);
            when(jdbc.update(startsWith("UPDATE outbox_event SET status='PUBLISHING'"), eq(1L))).thenReturn(1);
            publisher = new ReliableOutboxPublisher(jdbc, rabbit, new SimpleMeterRegistry(), "payment-service");
        }
    }
}
