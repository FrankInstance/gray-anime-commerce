package com.gray.anime.user.config;

import com.gray.anime.common.security.AccessTokenIssuer;
import com.gray.anime.common.security.PemKeyLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class UserBeans {
    @Bean
    AccessTokenIssuer accessTokenIssuer(
            @Value("${security.jwt.public-key-location}") Resource publicKey,
            @Value("${security.jwt.private-key-location}") Resource privateKey,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.key-id}") String keyId
    ) {
        return new AccessTokenIssuer(
                PemKeyLoader.loadPublicKey(publicKey),
                PemKeyLoader.loadPrivateKey(privateKey),
                issuer,
                audience,
                keyId
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
