package com.gray.anime.order.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gray.anime.order.domain.OrderItem;
import com.gray.anime.order.domain.OrderRecord;
import com.gray.anime.order.domain.OrderStatus;
import com.gray.anime.order.domain.PaymentRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gray.anime.eventing.DomainEventPublisher;
import com.gray.anime.eventing.DomainEventEnvelope;
import com.gray.anime.eventing.EventRoutes;
import com.gray.anime.eventing.InboxDeduplicator;
import com.gray.anime.eventing.PaymentConfirmedEvent;
import com.gray.anime.order.infrastructure.mapper.OrderItemMapper;
import com.gray.anime.order.infrastructure.mapper.OrderMapper;
import com.gray.anime.order.infrastructure.mapper.PaymentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class OrderLifecycleServiceTest {
    @BeforeAll
    static void initializeMybatisMetadata() {
        initializeTable(OrderRecord.class);
        initializeTable(PaymentRecord.class);
        initializeTable(OrderItem.class);
    }

    private static void initializeTable(Class<?> entityType) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), entityType.getName()),
                entityType
        );
    }

    @Test
    void expiredPendingOrderIsCancelledWithItsPayment() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderItemMapper itemMapper = mock(OrderItemMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        InboxDeduplicator inbox = mock(InboxDeduplicator.class);
        OrderExpirationPolicy expirationPolicy = new OrderExpirationPolicy(Duration.ofMinutes(10));
        OrderLifecycleService service = new OrderLifecycleService(
                orderMapper,
                itemMapper,
                paymentMapper,
                expirationPolicy,
                events,
                inbox,
                new ObjectMapper()
        );

        OrderRecord order = new OrderRecord();
        order.setId(42L);
        order.setOrderNo("OD-EXPIRED");
        order.setPaymentNo("PAY-EXPIRED");
        order.setUserId(7L);
        order.setOrderType("PRODUCT");
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        PaymentRecord payment = new PaymentRecord();
        payment.setId(43L);
        payment.setPaymentNo("PAY-EXPIRED");
        payment.setStatus("PENDING");
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(orderMapper.lockOwned("OD-EXPIRED", 7L)).thenReturn(order);
        when(paymentMapper.lockByPaymentNo("PAY-EXPIRED")).thenReturn(payment);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(paymentMapper.update(any(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of());

        int cancelled = service.cancelExpiredPendingOrders();

        assertEquals(1, cancelled);
        assertEquals(OrderStatus.CANCELLED.name(), order.getStatus());
        verify(paymentMapper).update(any(), any());
        verify(events).publish(org.mockito.ArgumentMatchers.eq(EventRoutes.ORDER_CANCELLED_PRODUCT),
                org.mockito.ArgumentMatchers.eq("Order"), org.mockito.ArgumentMatchers.eq("OD-EXPIRED"), any());
    }

    @Test
    void duplicatePaymentEventPaysAndDispatchesTheOrderOnlyOnce() throws Exception {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderItemMapper itemMapper = mock(OrderItemMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        InboxDeduplicator inbox = mock(InboxDeduplicator.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OrderLifecycleService service = new OrderLifecycleService(orderMapper, itemMapper, paymentMapper,
                new OrderExpirationPolicy(Duration.ofMinutes(10)), events, inbox, objectMapper);

        OrderRecord order = new OrderRecord();
        order.setId(51L);
        order.setOrderNo("OD-PAID");
        order.setUserId(7L);
        order.setOrderType("POINTS");
        order.setTotalCents(1000);
        order.setTotalPoints(100);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY-PAID");
        payment.setStatus("CONFIRMED");

        when(inbox.claim("order-payment-confirmed-v1", "event-paid")).thenReturn(true, false);
        when(orderMapper.lockOwned("OD-PAID", 7L)).thenReturn(order);
        when(paymentMapper.lockByPaymentNo("PAY-PAID")).thenReturn(payment);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        PaymentConfirmedEvent payload = new PaymentConfirmedEvent("PAY-PAID", "OD-PAID", 7L,
                1000, "DEMO", LocalDateTime.now());
        DomainEventEnvelope envelope = new DomainEventEnvelope("event-paid", EventRoutes.PAYMENT_CONFIRMED,
                "Payment", "PAY-PAID", 1, Instant.now(), objectMapper.valueToTree(payload));
        byte[] body = objectMapper.writeValueAsBytes(envelope);

        service.onPaymentConfirmed(body);
        service.onPaymentConfirmed(body);

        verify(orderMapper, times(1)).update(any(), any());
        verify(events, times(1)).publish(org.mockito.ArgumentMatchers.eq(EventRoutes.ORDER_PAID_POINTS),
                org.mockito.ArgumentMatchers.eq("Order"), org.mockito.ArgumentMatchers.eq("OD-PAID"), any());
    }
}
