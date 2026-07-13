package com.gray.anime.common.security;

import com.gray.anime.common.exception.BizException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AccessTokenVerifier {
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token",
            "Required access token claims are missing or invalid",
            null
    );

    private final NimbusJwtDecoder decoder;

    public AccessTokenVerifier(RSAPublicKey publicKey, String issuer, String audience, String keyId) {
        if (publicKey == null || publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("RSA access token public key must be at least 2048 bits");
        }
        String expectedAudience = requireText(audience, "audience");
        String expectedKeyId = requireText(keyId, "keyId");
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> standardClaims = JwtValidators.createDefaultWithIssuer(
                requireText(issuer, "issuer")
        );
        OAuth2TokenValidator<Jwt> applicationClaims = jwt -> validApplicationClaims(
                jwt,
                expectedAudience,
                expectedKeyId
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(standardClaims, applicationClaims));
    }

    public JwtClaims verify(String token) {
        if (!hasText(token)) {
            throw new BizException("TOKEN_INVALID", "Invalid access token");
        }
        try {
            Jwt jwt = decoder.decode(token);
            Long userId = Long.valueOf(jwt.getSubject());
            List<String> tokenRoles = jwt.getClaimAsStringList("roles");
            Set<String> roles = tokenRoles == null || tokenRoles.isEmpty()
                    ? Set.of("USER")
                    : Set.copyOf(new LinkedHashSet<>(tokenRoles));
            return new JwtClaims(userId, roles, jwt.getExpiresAt());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BizException("TOKEN_INVALID", "Invalid access token");
        }
    }

    private OAuth2TokenValidatorResult validApplicationClaims(Jwt jwt, String audience, String keyId) {
        boolean audienceMatches = jwt.getAudience().contains(audience);
        boolean keyMatches = keyId.equals(jwt.getHeaders().get("kid"));
        boolean subjectIsValid = isPositiveLong(jwt.getSubject());
        boolean rolesAreValid = validRoles(jwt.getClaims().get("roles"));
        boolean requiredClaimsExist = jwt.getIssuedAt() != null
                && jwt.getNotBefore() != null
                && jwt.getExpiresAt() != null
                && subjectIsValid
                && rolesAreValid
                && hasText(jwt.getClaimAsString(JwtClaimNames.JTI));
        return audienceMatches && keyMatches && requiredClaimsExist
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isPositiveLong(String value) {
        try {
            return hasText(value) && Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean validRoles(Object value) {
        if (!(value instanceof Collection<?> roles) || roles.isEmpty()) {
            return false;
        }
        return roles.stream().allMatch(role -> role instanceof String text && hasText(text));
    }

    private static String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
