package com.gray.anime.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AccessTokenIssuer {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final String keyId;

    public AccessTokenIssuer(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey,
            String issuer,
            String audience,
            String keyId
    ) {
        validateKeyPair(publicKey, privateKey);
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        this.encoder = new NimbusJwtEncoder(jwkSource);
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.keyId = requireText(keyId, "keyId");
    }

    public String issue(Long userId, Set<String> roles, long ttlSeconds) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }

        Instant issuedAt = Instant.now();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(issuedAt.plusSeconds(ttlSeconds))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles == null || roles.isEmpty() ? Set.of("USER") : Set.copyOf(roles))
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static void validateKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        if (publicKey == null || privateKey == null) {
            throw new IllegalArgumentException("RSA key pair must not be null");
        }
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("RSA access token keys must be at least 2048 bits");
        }
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalArgumentException("RSA public and private keys do not match");
        }
    }
}
