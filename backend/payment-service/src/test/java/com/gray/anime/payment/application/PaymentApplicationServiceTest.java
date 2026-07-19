package com.gray.anime.payment.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.eventing.DomainEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.gray.anime.payment.application.provider.PaymentCheckout;
import com.gray.anime.payment.application.provider.PaymentProvider;
import com.gray.anime.payment.application.provider.ProviderCheckoutSession;
import com.gray.anime.payment.domain.OrderRecord;
import com.gray.anime.payment.domain.PaymentRecord;
import com.gray.anime.payment.infrastructure.mapper.OrderMapper;
import com.gray.anime.payment.infrastructure.mapper.PaymentMapper;
import com.gray.anime.payment.infrastructure.provider.DemoPaymentProvider;
import com.gray.anime.payment.interfaces.dto.CheckoutSessionRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentApplicationServiceTest {
    @BeforeAll
    static void initializeMybatisMetadata() {
        initializeTable(PaymentRecord.class);
        initializeTable(OrderRecord.class);
    }

    private static void initializeTable(Class<?> entityType) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), entityType.getName()),
                entityType
        );
    }

    @Test
    void checkoutSessionUsesTheServerOwnedAmountAndAssignsTheProvider() {
        Fixture fixture = new Fixture();
        PaymentRecord payment = fixture.payment(7L, "CREATED", "UNASSIGNED");
        OrderRecord order = fixture.order(7L, "PENDING_PAYMENT");
        when(fixture.paymentMapper.selectOne(any())).thenReturn(payment);
        when(fixture.orderMapper.lockOwned("ORDER-1", 7L)).thenReturn(order);
        when(fixture.paymentMapper.lockOwned("PAY-1", 7L)).thenReturn(payment);
        when(fixture.jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        when(fixture.provider.createSession(any())).thenReturn(new ProviderCheckoutSession(
                DemoPaymentProvider.CODE,
                "DEMO-PAY-1",
                DemoPaymentProvider.INTERACTION_MODE,
                null,
                LocalDateTime.now().plusMinutes(10)
        ));

        var session = fixture.service.createCheckoutSession(
                new CurrentUser(7L, Set.of("USER")),
                new CheckoutSessionRequest("PAY-1")
        );

        assertThat(session.provider()).isEqualTo(DemoPaymentProvider.CODE);
        assertThat(session.paymentNo()).isEqualTo("PAY-1");
        ArgumentCaptor<PaymentCheckout> checkout = ArgumentCaptor.forClass(PaymentCheckout.class);
        verify(fixture.provider).createSession(checkout.capture());
        assertThat(checkout.getValue().amountCents()).isEqualTo(1000);
        assertThat(payment.getChannel()).isEqualTo(DemoPaymentProvider.CODE);
    }

    @Test
    void anotherUserCannotOpenOrConfirmThePayment() {
        Fixture fixture = new Fixture();
        when(fixture.paymentMapper.selectOne(any())).thenReturn(null);
        CurrentUser attacker = new CurrentUser(7L, Set.of("USER"));

        assertThatThrownBy(() -> fixture.service.createCheckoutSession(attacker, new CheckoutSessionRequest("PAY-1")))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.code()).isEqualTo("PAYMENT_NOT_FOUND"));
        assertThatThrownBy(() -> fixture.service.confirmDemo(attacker, "PAY-1"))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.code()).isEqualTo("PAYMENT_NOT_FOUND"));
        verify(fixture.provider, never()).createSession(any());
    }

    @Test
    void confirmingAnAlreadyConfirmedPaymentIsIdempotent() {
        Fixture fixture = new Fixture();
        PaymentRecord payment = fixture.payment(7L, "CONFIRMED", DemoPaymentProvider.CODE);
        payment.setConfirmedAt(LocalDateTime.now());
        when(fixture.paymentMapper.selectOne(any())).thenReturn(payment);
        when(fixture.orderMapper.lockOwned("ORDER-1", 7L)).thenReturn(fixture.order(7L, "PAID"));
        when(fixture.paymentMapper.lockOwned("PAY-1", 7L)).thenReturn(payment);

        var result = fixture.service.confirmDemo(new CurrentUser(7L, Set.of("USER")), "PAY-1");

        assertThat(result.status()).isEqualTo("CONFIRMED");
        verify(fixture.paymentMapper, never()).update(any(), any());
        verify(fixture.orderMapper, never()).update(any(), any());
    }

    private static final class Fixture {
        private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
        private final OrderMapper orderMapper = mock(OrderMapper.class);
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final DomainEventPublisher events = mock(DomainEventPublisher.class);
        private final PaymentProvider provider = mock(PaymentProvider.class);
        private final PaymentApplicationService service;

        private Fixture() {
            when(provider.code()).thenReturn(DemoPaymentProvider.CODE);
            service = new PaymentApplicationService(
                    paymentMapper,
                    orderMapper,
                    jdbc,
                    events,
                    provider,
                    new SimpleMeterRegistry()
            );
        }

        private PaymentRecord payment(Long userId, String status, String channel) {
            PaymentRecord payment = new PaymentRecord();
            payment.setId(1L);
            payment.setPaymentNo("PAY-1");
            payment.setOrderNo("ORDER-1");
            payment.setUserId(userId);
            payment.setAmountCents(1000);
            payment.setChannel(channel);
            payment.setStatus(status);
            payment.setCreatedAt(LocalDateTime.now());
            return payment;
        }

        private OrderRecord order(Long userId, String status) {
            OrderRecord order = new OrderRecord();
            order.setId(1L);
            order.setOrderNo("ORDER-1");
            order.setUserId(userId);
            order.setOrderType("POINTS");
            order.setTotalCents(1000);
            order.setTotalPoints(100);
            order.setStatus(status);
            order.setUpdatedAt(LocalDateTime.now());
            return order;
        }
    }
}
