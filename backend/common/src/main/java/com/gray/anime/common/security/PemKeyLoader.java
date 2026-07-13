package com.gray.anime.common.security;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PemKeyLoader {
    private PemKeyLoader() {
    }

    public static RSAPrivateKey loadPrivateKey(Resource resource) {
        byte[] encoded = decodePem(resource, "PRIVATE KEY");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Invalid RSA private key: " + resource.getDescription(), exception);
        }
    }

    public static RSAPublicKey loadPublicKey(Resource resource) {
        byte[] encoded = decodePem(resource, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Invalid RSA public key: " + resource.getDescription(), exception);
        }
    }

    private static byte[] decodePem(Resource resource, String type) {
        try {
            String pem = resource.getContentAsString(StandardCharsets.UTF_8);
            String content = pem
                    .replace("-----BEGIN " + type + "-----", "")
                    .replace("-----END " + type + "-----", "")
                    .replaceAll("\\s", "");
            if (content.isBlank()) {
                throw new IllegalStateException("Empty PEM key: " + resource.getDescription());
            }
            return Base64.getDecoder().decode(content);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to read PEM key: " + resource.getDescription(), exception);
        }
    }
}
