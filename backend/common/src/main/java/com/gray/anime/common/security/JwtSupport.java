package com.gray.anime.common.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gray.anime.common.exception.BizException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JwtSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final byte[] secret;
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder urlDecoder = Base64.getUrlDecoder();

    public JwtSupport(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(Long userId, Set<String> roles, long ttlSeconds) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", userId.toString());
            payload.put("roles", roles);
            payload.put("exp", Instant.now().plusSeconds(ttlSeconds).getEpochSecond());
            String unsigned = encodeJson(header) + "." + encodeJson(payload);
            return unsigned + "." + sign(unsigned);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to issue JWT", exception);
        }
    }

    public JwtClaims verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BizException("TOKEN_INVALID", "Invalid token");
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsigned), parts[2])) {
                throw new BizException("TOKEN_INVALID", "Invalid token signature");
            }
            Map<String, Object> payload = MAPPER.readValue(urlDecoder.decode(parts[1]), new TypeReference<>() {
            });
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.ofEpochSecond(exp).isBefore(Instant.now())) {
                throw new BizException("TOKEN_EXPIRED", "Token expired");
            }
            Object rolesValue = payload.get("roles");
            Set<String> roles = Set.of("USER");
            if (rolesValue instanceof Iterable<?> iterable) {
                roles = java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                        .map(Object::toString)
                        .collect(Collectors.toSet());
                if (roles.isEmpty()) {
                    roles = Set.of("USER");
                }
            }
            return new JwtClaims(Long.valueOf(payload.get("sub").toString()), roles, Instant.ofEpochSecond(exp));
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("TOKEN_INVALID", "Invalid token");
        }
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return urlEncoder.encodeToString(MAPPER.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return urlEncoder.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        int result = a.length ^ b.length;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
