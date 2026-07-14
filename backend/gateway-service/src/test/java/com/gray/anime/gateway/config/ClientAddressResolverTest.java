package com.gray.anime.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressResolverTest {
    @Test
    void ignoresForwardedHeadersWhenNoProxyIsTrusted() {
        MockServerWebExchange exchange = exchange("203.0.113.10", "198.51.100.99");

        assertThat(new ClientAddressResolver(0).resolve(exchange)).isEqualTo("203.0.113.10");
    }

    @Test
    void usesTheAddressAppendedByTheTrustedEdgeProxy() {
        MockServerWebExchange exchange = exchange("172.20.0.8", "198.51.100.99, 203.0.113.10");

        assertThat(new ClientAddressResolver(1).resolve(exchange)).isEqualTo("203.0.113.10");
    }

    @Test
    void fallsBackToTheConnectionAddressForInvalidForwardedData() {
        MockServerWebExchange exchange = exchange("172.20.0.8", "not-an-ip");

        assertThat(new ClientAddressResolver(1).resolve(exchange)).isEqualTo("172.20.0.8");
    }

    private MockServerWebExchange exchange(String remoteAddress, String forwardedFor) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress(remoteAddress, 443))
                .header("X-Forwarded-For", forwardedFor)
                .build();
        return MockServerWebExchange.from(request);
    }
}
