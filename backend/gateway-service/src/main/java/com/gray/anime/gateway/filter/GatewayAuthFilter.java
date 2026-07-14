package com.gray.anime.gateway.filter;

import com.gray.anime.common.api.TraceIds;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.AccessTokenVerifier;
import com.gray.anime.common.security.ApiAccessPolicy;
import com.gray.anime.common.security.JwtClaims;
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
    public static final String AUTHENTICATED_USER_ID_ATTRIBUTE = GatewayAuthFilter.class.getName() + ".userId";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private final AccessTokenVerifier accessTokenVerifier;

    public GatewayAuthFilter(AccessTokenVerifier accessTokenVerifier) {
        this.accessTokenVerifier = accessTokenVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestedTraceId = exchange.getRequest().getHeaders().getFirst(TraceIds.HEADER);
        String traceId = requestedTraceId == null || requestedTraceId.isBlank()
                ? TraceIds.newTraceId()
                : requestedTraceId;
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        exchange.getResponse().getHeaders().set(TraceIds.HEADER, traceId);

        try {
            ServerHttpRequest.Builder request = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.set(TraceIds.HEADER, traceId);
                        headers.remove(USER_ID_HEADER);
                        headers.remove(USER_ROLES_HEADER);
                    });
            JwtClaims claims = resolveClaims(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            if (claims != null) {
                exchange.getAttributes().put(AUTHENTICATED_USER_ID_ATTRIBUTE, claims.userId());
            }
            if (ApiAccessPolicy.isInternal(path)) {
                return reject(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", traceId);
            }
            if (ApiAccessPolicy.requiresLogin(path) && claims == null) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Login required", traceId);
            }
            if (ApiAccessPolicy.requiresAdmin(path) && (claims == null || !isAdmin(claims.roles()))) {
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
        return accessTokenVerifier.verify(authorization.substring("Bearer ".length()));
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
