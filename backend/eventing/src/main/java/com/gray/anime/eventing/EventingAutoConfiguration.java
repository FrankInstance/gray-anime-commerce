package com.gray.anime.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

@AutoConfiguration
@EnableScheduling
public class EventingAutoConfiguration {
    @Bean
    DomainEventPublisher domainEventPublisher(JdbcTemplate jdbc, ObjectMapper objectMapper, Environment environment) {
        return new DomainEventPublisher(jdbc, objectMapper,
                environment.getProperty("spring.application.name", "unknown-service"));
    }

    @Bean
    InboxDeduplicator inboxDeduplicator(JdbcTemplate jdbc) {
        return new InboxDeduplicator(jdbc);
    }

    @Bean
    ReliableOutboxPublisher reliableOutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate,
                                                     MeterRegistry meters, Environment environment) {
        return new ReliableOutboxPublisher(jdbc, rabbitTemplate, meters,
                environment.getProperty("spring.application.name", "unknown-service"));
    }

    @Bean
    DirectExchange grayEventsExchange() {
        return ExchangeBuilder.directExchange(EventRoutes.EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange grayDeadEventsExchange() {
        return ExchangeBuilder.directExchange(EventRoutes.DEAD_EXCHANGE).durable(true).build();
    }

    @Bean
    Declarables grayEventTopology(DirectExchange grayEventsExchange, DirectExchange grayDeadEventsExchange) {
        Queue orderPayment = queue(EventRoutes.ORDER_PAYMENT_QUEUE);
        Queue orderFulfillment = queue(EventRoutes.ORDER_FULFILLMENT_QUEUE);
        Queue inventory = queue(EventRoutes.INVENTORY_FULFILLMENT_QUEUE);
        Queue user = queue(EventRoutes.USER_FULFILLMENT_QUEUE);
        Queue orderPaymentDead = deadQueue(EventRoutes.ORDER_PAYMENT_QUEUE);
        Queue orderFulfillmentDead = deadQueue(EventRoutes.ORDER_FULFILLMENT_QUEUE);
        Queue inventoryDead = deadQueue(EventRoutes.INVENTORY_FULFILLMENT_QUEUE);
        Queue userDead = deadQueue(EventRoutes.USER_FULFILLMENT_QUEUE);

        return new Declarables(
                orderPayment, orderFulfillment, inventory, user,
                orderPaymentDead, orderFulfillmentDead, inventoryDead, userDead,
                BindingBuilder.bind(orderPayment).to(grayEventsExchange).with(EventRoutes.PAYMENT_CONFIRMED),
                BindingBuilder.bind(orderFulfillment).to(grayEventsExchange).with(EventRoutes.FULFILLMENT_COMPLETED),
                BindingBuilder.bind(orderFulfillment).to(grayEventsExchange).with(EventRoutes.FULFILLMENT_FAILED),
                BindingBuilder.bind(inventory).to(grayEventsExchange).with(EventRoutes.ORDER_PAID_PRODUCT),
                BindingBuilder.bind(inventory).to(grayEventsExchange).with(EventRoutes.ORDER_CANCELLED_PRODUCT),
                BindingBuilder.bind(user).to(grayEventsExchange).with(EventRoutes.ORDER_PAID_VIP),
                BindingBuilder.bind(user).to(grayEventsExchange).with(EventRoutes.ORDER_PAID_POINTS),
                BindingBuilder.bind(orderPaymentDead).to(grayDeadEventsExchange).with(EventRoutes.ORDER_PAYMENT_QUEUE),
                BindingBuilder.bind(orderFulfillmentDead).to(grayDeadEventsExchange).with(EventRoutes.ORDER_FULFILLMENT_QUEUE),
                BindingBuilder.bind(inventoryDead).to(grayDeadEventsExchange).with(EventRoutes.INVENTORY_FULFILLMENT_QUEUE),
                BindingBuilder.bind(userDead).to(grayDeadEventsExchange).with(EventRoutes.USER_FULFILLMENT_QUEUE)
        );
    }

    private Queue queue(String name) {
        return QueueBuilder.durable(name)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EventRoutes.DEAD_EXCHANGE,
                        "x-dead-letter-routing-key", name))
                .build();
    }

    private Queue deadQueue(String source) {
        return QueueBuilder.durable(source + ".dead").build();
    }
}
