package com.gray.anime.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@AutoConfiguration
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccessTokenServletConfig {
    @Bean
    @ConditionalOnMissingBean
    AccessTokenVerifier accessTokenVerifier(
            @Value("${JWT_PUBLIC_KEY_LOCATION:file:./deploy/keys/access-token-public.pem}") Resource publicKey,
            @Value("${JWT_ISSUER:gray-auth}") String issuer,
            @Value("${JWT_AUDIENCE:gray-api}") String audience,
            @Value("${JWT_KEY_ID:gray-access-2026-01}") String keyId
    ) {
        return new AccessTokenVerifier(PemKeyLoader.loadPublicKey(publicKey), issuer, audience, keyId);
    }

    @Bean
    FilterRegistrationBean<AccessTokenAuthenticationFilter> accessTokenAuthenticationFilter(
            AccessTokenVerifier verifier
    ) {
        FilterRegistrationBean<AccessTokenAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AccessTokenAuthenticationFilter(verifier));
        registration.setName("accessTokenAuthenticationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
