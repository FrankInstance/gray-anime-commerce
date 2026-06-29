package com.gray.anime.gateway.config;

import com.gray.anime.common.security.JwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayBeans {
    @Bean
    JwtSupport jwtSupport(@Value("${security.jwt.secret}") String secret) {
        return new JwtSupport(secret);
    }
}
