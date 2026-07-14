package com.gray.anime.gateway.config;

import com.gray.anime.gateway.filter.GatewayAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitBeans {
    @Bean
    ClientAddressResolver clientAddressResolver(
            @Value("${operations.rate-limit.trusted-proxy-hops:0}") int trustedProxyHops
    ) {
        return new ClientAddressResolver(trustedProxyHops);
    }

    @Bean
    @Primary
    KeyResolver clientAddressKeyResolver(ClientAddressResolver clientAddressResolver) {
        return exchange -> Mono.just("ip:" + clientAddressResolver.resolve(exchange));
    }

    @Bean
    KeyResolver userOrClientAddressKeyResolver(ClientAddressResolver clientAddressResolver) {
        return exchange -> {
            Long userId = exchange.getAttribute(GatewayAuthFilter.AUTHENTICATED_USER_ID_ATTRIBUTE);
            String key = userId == null
                    ? "ip:" + clientAddressResolver.resolve(exchange)
                    : "user:" + userId;
            return Mono.just(key);
        };
    }
}
