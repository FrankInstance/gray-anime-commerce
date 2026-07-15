package com.gray.anime.gateway.filter;

import com.gray.anime.common.api.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRequestLoggingFilterTest {
    private final GatewayRequestLoggingFilter filter = new GatewayRequestLoggingFilter();

    @Test
    void returnsOneTraceHeaderWhenDownstreamAlsoAddsIt() {
        String traceId = "0123456789abcdef01234567";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/works")
                        .header(TraceIds.HEADER, traceId)
        );
        AtomicReference<String> forwardedTraceId = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwardedTraceId.set(current.getRequest().getHeaders().getFirst(TraceIds.HEADER));
            current.getResponse().getHeaders().add(TraceIds.HEADER, traceId);
            return current.getResponse().setComplete();
        }).block();

        assertThat(forwardedTraceId.get()).isEqualTo(traceId);
        assertThat(exchange.getResponse().getHeaders().get(TraceIds.HEADER))
                .containsExactly(traceId);
    }
}
