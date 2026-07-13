package com.gray.anime.gateway.config;

import com.gray.anime.common.security.AccessTokenVerifier;
import com.gray.anime.common.security.PemKeyLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class GatewayBeans {
    @Bean
    AccessTokenVerifier accessTokenVerifier(
            @Value("${security.jwt.public-key-location}") Resource publicKey,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.key-id}") String keyId
    ) {
        return new AccessTokenVerifier(PemKeyLoader.loadPublicKey(publicKey), issuer, audience, keyId);
    }
}
