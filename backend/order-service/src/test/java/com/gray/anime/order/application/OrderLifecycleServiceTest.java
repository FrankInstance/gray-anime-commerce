package com.gray.anime.order.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        OutboxEventMapper outboxMapper = mock(OutboxEventMapper.class);
        InventoryClient inventoryClient = mock(InventoryClient.class);
        OrderExpirationPolicy expirationPolicy = new OrderExpirationPolicy(Duration.ofMinutes(10));
        OrderLifecycleService service = new OrderLifecycleService(
                orderMapper,
                itemMapper,
                paymentMapper,
                outboxMapper,
                inventoryClient,
                expirationPolicy
        );

        OrderRecord order = new OrderRecord();
        order.setId(42L);
        order.setOrderNo("OD-EXPIRED");
        order.setPaymentNo("PAY-EXPIRED");
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of());

        int cancelled = service.cancelExpiredPendingOrders();

        assertEquals(1, cancelled);
        assertEquals(OrderStatus.CANCELLED.name(), order.getStatus());
        verify(paymentMapper).update(any(), any());
        verify(outboxMapper).insert(any(OutboxEvent.class));
    }
}
