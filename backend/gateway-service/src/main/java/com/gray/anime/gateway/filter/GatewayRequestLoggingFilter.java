package com.gray.anime.gateway.filter;

import com.gray.anime.common.api.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

@Component
public class GatewayRequestLoggingFilter implements GlobalFilter, Ordered {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAt = System.nanoTime();
        String traceId = TraceIds.resolve(exchange.getRequest().getHeaders().getFirst(TraceIds.HEADER));
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TraceIds.HEADER, traceId))
                .build();
        ServerWebExchange observedExchange = exchange.mutate().request(request).build();
        observedExchange.getResponse().beforeCommit(() -> {
            observedExchange.getResponse().getHeaders().set(TraceIds.HEADER, traceId);
            return Mono.empty();
        });

        return chain.filter(observedExchange).doFinally(signalType -> {
            HttpStatusCode status = observedExchange.getResponse().getStatusCode();
            int statusCode = status == null ? 0 : status.value();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            LOGGER.atInfo()
                    .addKeyValue(TraceIds.MDC_KEY, traceId)
                    .addKeyValue("httpMethod", method)
                    .addKeyValue("httpPath", path)
                    .addKeyValue("httpStatus", statusCode)
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("signal", signalType.name())
                    .log("gateway request completed: traceId={} {} {} -> {} in {} ms",
                            traceId, method, path, statusCode, durationMs);
        });
    }

    @Override
    public int getOrder() {
        return -110;
    }
}
