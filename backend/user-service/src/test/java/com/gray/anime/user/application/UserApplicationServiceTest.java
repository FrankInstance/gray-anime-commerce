package com.gray.anime.user.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.AccessTokenIssuer;
import com.gray.anime.user.domain.AppUser;
import com.gray.anime.user.domain.AuthSession;
import com.gray.anime.user.domain.NotificationMessage;
import com.gray.anime.user.domain.PasswordResetToken;
import com.gray.anime.user.infrastructure.mapper.AppUserMapper;
import com.gray.anime.user.infrastructure.mapper.AuthSessionMapper;
import com.gray.anime.user.infrastructure.mapper.NotificationMessageMapper;
import com.gray.anime.user.infrastructure.mapper.PasswordResetTokenMapper;
import com.gray.anime.user.infrastructure.mapper.PointsLedgerMapper;
import com.gray.anime.user.interfaces.dto.LoginRequest;
import com.gray.anime.user.interfaces.dto.PasswordResetDevResponse;
import com.gray.anime.user.interfaces.dto.PasswordResetRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserApplicationServiceTest {
    private static final long ACCESS_TTL_SECONDS = 900;
    private static final long SESSION_IDLE_TTL_SECONDS = 72 * 60 * 60;

    private final AppUserMapper userMapper = mock(AppUserMapper.class);
    private final AuthSessionMapper authSessionMapper = mock(AuthSessionMapper.class);
    private final PasswordResetTokenMapper resetTokenMapper = mock(PasswordResetTokenMapper.class);
    private final NotificationMessageMapper notificationMapper = mock(NotificationMessageMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);
    private final UserApplicationService service = new UserApplicationService(
            userMapper,
            authSessionMapper,
            mock(PointsLedgerMapper.class),
            resetTokenMapper,
            notificationMapper,
            passwordEncoder,
            accessTokenIssuer,
            ACCESS_TTL_SECONDS,
            SESSION_IDLE_TTL_SECONDS,
            "disabled"
    );

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), AuthSession.class.getName()),
                AuthSession.class
        );
    }

    @Test
    void loginCreatesHashedRefreshSessionWithThreeDayIdleExpiry() {
        AppUser user = activeUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret123", user.getPasswordHash())).thenReturn(true);
        when(accessTokenIssuer.issue(user.getId(), Set.of("USER"), ACCESS_TTL_SECONDS)).thenReturn("access-token");

        LocalDateTime before = LocalDateTime.now().plusSeconds(SESSION_IDLE_TTL_SECONDS - 1);
        AuthSessionResult result = service.login(new LoginRequest(user.getEmail(), "secret123"));
        LocalDateTime after = LocalDateTime.now().plusSeconds(SESSION_IDLE_TTL_SECONDS + 1);

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionMapper).insert(sessionCaptor.capture());
        AuthSession session = sessionCaptor.getValue();
        assertEquals(user.getId(), session.getUserId());
        assertEquals(64, session.getTokenHash().length());
        assertNotEquals(result.refreshToken(), session.getTokenHash());
        assertTrue(!session.getExpiresAt().isBefore(before) && !session.getExpiresAt().isAfter(after));
        assertEquals("access-token", result.tokenResponse().accessToken());
        assertEquals(ACCESS_TTL_SECONDS, result.tokenResponse().expiresIn());
    }

    @Test
    void refreshRotatesTheRefreshTokenAndIssuesANewAccessToken() {
        AppUser user = activeUser();
        AuthSession session = activeSession(user.getId());
        when(authSessionMapper.selectOne(any())).thenReturn(session);
        when(userMapper.selectById(user.getId())).thenReturn(user);
        when(authSessionMapper.update(isNull(), any())).thenReturn(1);
        when(accessTokenIssuer.issue(user.getId(), Set.of("USER"), ACCESS_TTL_SECONDS)).thenReturn("next-access-token");

        AuthSessionResult result = service.refreshSession("current-refresh-token");

        assertNotEquals("current-refresh-token", result.refreshToken());
        assertEquals("next-access-token", result.tokenResponse().accessToken());
        verify(authSessionMapper).update(isNull(), any());
    }

    @Test
    void expiredRefreshSessionRequiresLoginAgain() {
        AuthSession session = activeSession(7L);
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(authSessionMapper.selectOne(any())).thenReturn(session);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.refreshSession("expired-refresh-token")
        );

        assertEquals("SESSION_EXPIRED", exception.code());
    }

    @Test
    void productionPasswordResetFailsClosedWhenNoDeliveryProviderIsConfigured() {
        BizException exception = assertThrows(
                BizException.class,
                () -> service.requestPasswordReset(new PasswordResetRequest("reader@example.com"))
        );

        assertEquals("PASSWORD_RESET_UNAVAILABLE", exception.code());
        verifyNoInteractions(resetTokenMapper, notificationMapper);
    }

    @Test
    void developmentPasswordResetReturnsTheLocalVerificationToken() {
        AppUser user = activeUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        UserApplicationService developmentService = new UserApplicationService(
                userMapper,
                authSessionMapper,
                mock(PointsLedgerMapper.class),
                resetTokenMapper,
                notificationMapper,
                passwordEncoder,
                accessTokenIssuer,
                ACCESS_TTL_SECONDS,
                SESSION_IDLE_TTL_SECONDS,
                "development"
        );

        PasswordResetDevResponse response = developmentService.requestPasswordReset(new PasswordResetRequest(user.getEmail()));

        assertEquals("EMAIL_DEV_SIMULATOR", response.channel());
        assertTrue(response.devToken() != null && response.devToken().matches("\\d{6}"));
        verify(resetTokenMapper).insert(any(PasswordResetToken.class));
        verify(notificationMapper).insert(any(NotificationMessage.class));
    }

    private AppUser activeUser() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setEmail("reader@example.com");
        user.setUsername("Reader");
        user.setPasswordHash("encoded-password");
        user.setRoles("USER");
        user.setStatus("ACTIVE");
        user.setPoints(0);
        return user;
    }

    private AuthSession activeSession(Long userId) {
        AuthSession session = new AuthSession();
        session.setId(11L);
        session.setUserId(userId);
        session.setTokenHash("stored-hash");
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        session.setRevoked(false);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }
}
