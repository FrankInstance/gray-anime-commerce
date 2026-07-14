package com.gray.anime.user.infrastructure.bootstrap;

import com.gray.anime.user.infrastructure.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UserDataSeederProfileTest {
    @Test
    void registersDefaultAccountsOnlyForLocalAndTestProfiles() {
        try (AnnotationConfigApplicationContext local = context("local")) {
            assertThat(local.getBeansOfType(UserDataSeeder.class)).hasSize(1);
        }
        try (AnnotationConfigApplicationContext production = context("prod")) {
            assertThat(production.getBeansOfType(UserDataSeeder.class)).isEmpty();
        }
    }

    private AnnotationConfigApplicationContext context(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.registerBean(AppUserMapper.class, () -> mock(AppUserMapper.class));
        context.registerBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));
        context.register(UserDataSeeder.class);
        context.refresh();
        return context;
    }
}
