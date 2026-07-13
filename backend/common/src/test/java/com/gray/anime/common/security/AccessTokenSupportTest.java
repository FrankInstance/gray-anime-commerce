package com.gray.anime.common.security;

import com.gray.anime.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenSupportTest {
    private static final String ISSUER = "gray-auth";
    private static final String AUDIENCE = "gray-api";
    private static final String KEY_ID = "gray-access-2026-01";

    @Test
    void rsaIssuerAndPublicKeyVerifierShareOnlyThePublicKey() throws Exception {
        KeyPair keyPair = keyPair();
        AccessTokenIssuer issuer = issuer(keyPair, ISSUER, AUDIENCE, KEY_ID);
        AccessTokenVerifier verifier = new AccessTokenVerifier(
                (RSAPublicKey) keyPair.getPublic(), ISSUER, AUDIENCE, KEY_ID
        );

        JwtClaims claims = verifier.verify(issuer.issue(7L, Set.of("USER", "VIP"), 60));

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.roles()).containsExactlyInAnyOrder("USER", "VIP");
    }

    @Test
    void rejectsTokenSignedByAnotherPrivateKey() throws Exception {
        KeyPair trusted = keyPair();
        KeyPair attacker = keyPair();
        AccessTokenVerifier verifier = new AccessTokenVerifier(
                (RSAPublicKey) trusted.getPublic(), ISSUER, AUDIENCE, KEY_ID
        );
        String forgedToken = issuer(attacker, ISSUER, AUDIENCE, KEY_ID).issue(7L, Set.of("ADMIN"), 60);

        assertThatThrownBy(() -> verifier.verify(forgedToken))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("TOKEN_INVALID");
    }

    @Test
    void rejectsUnexpectedIssuerAudienceAndKeyId() throws Exception {
        KeyPair keyPair = keyPair();
        AccessTokenVerifier verifier = new AccessTokenVerifier(
                (RSAPublicKey) keyPair.getPublic(), ISSUER, AUDIENCE, KEY_ID
        );

        assertRejected(verifier, issuer(keyPair, "other-auth", AUDIENCE, KEY_ID));
        assertRejected(verifier, issuer(keyPair, ISSUER, "other-api", KEY_ID));
        assertRejected(verifier, issuer(keyPair, ISSUER, AUDIENCE, "other-key"));
    }

    @Test
    void rejectsMismatchedAndWeakRsaKeysAtConfigurationTime() throws Exception {
        KeyPair first = keyPair();
        KeyPair second = keyPair();
        KeyPairGenerator weakGenerator = KeyPairGenerator.getInstance("RSA");
        weakGenerator.initialize(1024);
        KeyPair weak = weakGenerator.generateKeyPair();

        assertThatThrownBy(() -> new AccessTokenIssuer(
                (RSAPublicKey) first.getPublic(),
                (RSAPrivateKey) second.getPrivate(),
                ISSUER,
                AUDIENCE,
                KEY_ID
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessTokenVerifier(
                (RSAPublicKey) weak.getPublic(), ISSUER, AUDIENCE, KEY_ID
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRejected(AccessTokenVerifier verifier, AccessTokenIssuer issuer) {
        String token = issuer.issue(7L, Set.of("USER"), 60);
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BizException.class);
    }

    private AccessTokenIssuer issuer(KeyPair keyPair, String issuer, String audience, String keyId) {
        return new AccessTokenIssuer(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate(),
                issuer,
                audience,
                keyId
        );
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
