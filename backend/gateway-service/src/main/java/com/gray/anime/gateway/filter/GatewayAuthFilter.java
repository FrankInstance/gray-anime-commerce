package com.gray.anime.gateway.filter;

import com.gray.anime.common.api.TraceIds;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.JwtClaims;
import com.gray.anime.common.security.JwtSupport;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {
    private final JwtSupport jwtSupport;

    public GatewayAuthFilter(JwtSupport jwtSupport) {
        this.jwtSupport = jwtSupport;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIds.HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIds.newTraceId();
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        exchange.getResponse().getHeaders().set(TraceIds.HEADER, traceId);

        try {
            ServerHttpRequest.Builder request = exchange.getRequest().mutate()
                    .header(TraceIds.HEADER, traceId);
            JwtClaims claims = resolveClaims(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            if (claims != null) {
                request.header("X-User-Id", claims.userId().toString());
                request.header("X-User-Roles", String.join(",", claims.roles()));
            }
            if (requiresLogin(path) && claims == null) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Login required", traceId);
            }
            if (path.startsWith("/api/v1/admin/") && (claims == null || !isAdmin(claims.roles()))) {
                return reject(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin role required", traceId);
            }
            return chain.filter(exchange.mutate().request(request.build()).build());
        } catch (BizException exception) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, exception.code(), exception.getMessage(), traceId);
        }
    }

    private JwtClaims resolveClaims(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return jwtSupport.verify(authorization.substring("Bearer ".length()));
    }

    private boolean requiresLogin(String path) {
        return path.startsWith("/api/v1/users/")
                || path.startsWith("/api/v1/checkins")
                || path.startsWith("/api/v1/cart")
                || path.startsWith("/api/v1/orders")
                || path.startsWith("/api/v1/reading")
                || path.startsWith("/api/v1/vip")
                || path.contains("/bookshelf")
                || path.contains("/purchase")
                || path.startsWith("/api/v1/payments");
    }

    private boolean isAdmin(Set<String> roles) {
        return roles.contains("ADMIN") || roles.contains("SUPER_ADMIN");
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String message, String traceId) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null,\"traceId\":\"" + traceId + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
