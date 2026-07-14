package com.gray.anime.gateway.config;

import com.gray.anime.gateway.filter.GatewayAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitBeansTest {
    private final RateLimitBeans beans = new RateLimitBeans();
    private final ClientAddressResolver addressResolver = new ClientAddressResolver(0);

    @Test
    void unauthenticatedOperationsAreLimitedByClientAddress() {
        MockServerWebExchange exchange = exchange();

        assertThat(beans.clientAddressKeyResolver(addressResolver).resolve(exchange).block())
                .isEqualTo("ip:203.0.113.10");
    }

    @Test
    void authenticatedOperationsAreLimitedByUserId() {
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GatewayAuthFilter.AUTHENTICATED_USER_ID_ATTRIBUTE, 42L);

        assertThat(beans.userOrClientAddressKeyResolver(addressResolver).resolve(exchange).block())
                .isEqualTo("user:42");
    }

    @Test
    void clientAddressResolverIsTheUnambiguousFrameworkDefault() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test",
                    Map.of("operations.rate-limit.trusted-proxy-hops", "0")
            ));
            context.register(RateLimitBeans.class);
            context.refresh();

            assertThat(context.getBean(KeyResolver.class))
                    .isSameAs(context.getBean("clientAddressKeyResolver"));
        }
    }

    private MockServerWebExchange exchange() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/payments/checkout-session")
                .remoteAddress(new InetSocketAddress("203.0.113.10", 443))
                .build();
        return MockServerWebExchange.from(request);
    }
}
