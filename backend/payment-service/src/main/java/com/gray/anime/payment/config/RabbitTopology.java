package com.gray.anime.payment.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {
    @Bean
    DirectExchange grayEventsExchange() {
        return new DirectExchange("gray.events", true, false);
    }
}
