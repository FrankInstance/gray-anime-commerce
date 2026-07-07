package com.gray.anime.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.common.security.JwtSupport;
import com.gray.anime.user.domain.AppUser;
import com.gray.anime.user.domain.NotificationMessage;
import com.gray.anime.user.domain.PasswordResetToken;
import com.gray.anime.user.domain.PointsLedger;
import com.gray.anime.user.infrastructure.mapper.AppUserMapper;
import com.gray.anime.user.infrastructure.mapper.NotificationMessageMapper;
import com.gray.anime.user.infrastructure.mapper.PasswordResetTokenMapper;
import com.gray.anime.user.infrastructure.mapper.PointsLedgerMapper;
import com.gray.anime.user.interfaces.dto.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserApplicationService {
    private static final long ACCESS_TOKEN_TTL_SECONDS = 7200;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserMapper userMapper;
    private final PointsLedgerMapper pointsLedgerMapper;
    private final PasswordResetTokenMapper resetTokenMapper;
    private final NotificationMessageMapper notificationMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtSupport jwtSupport;

    public UserApplicationService(
            AppUserMapper userMapper,
            PointsLedgerMapper pointsLedgerMapper,
            PasswordResetTokenMapper resetTokenMapper,
            NotificationMessageMapper notificationMapper,
            PasswordEncoder passwordEncoder,
            JwtSupport jwtSupport
    ) {
        this.userMapper = userMapper;
        this.pointsLedgerMapper = pointsLedgerMapper;
        this.resetTokenMapper = resetTokenMapper;
        this.notificationMapper = notificationMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtSupport = jwtSupport;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        AppUser existed = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getEmail, email));
        if (existed != null) {
            throw new BizException("EMAIL_EXISTS", "Email already registered");
        }
        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles("USER");
        user.setStatus("ACTIVE");
        user.setPoints(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return tokenFor(user);
    }

    public TokenResponse login(LoginRequest request) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getEmail, normalizeEmail(request.email())));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException("BAD_CREDENTIALS", "Email or password is incorrect");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException("USER_DISABLED", "User is not active");
        }
        return tokenFor(user);
    }

    public UserProfile me(CurrentUser currentUser) {
        AppUser user = requireUser(currentUser.id());
        return profile(user);
    }

    @Transactional
    public UserProfile updateMe(CurrentUser currentUser, UpdateProfileRequest request) {
        AppUser user = requireUser(currentUser.id());
        if (request.username() != null && !request.username().isBlank()) {
            user.setUsername(request.username().trim());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return profile(user);
    }

    @Transactional
    public CheckinResponse checkin(CurrentUser currentUser) {
        AppUser user = requireUser(currentUser.id());
        LocalDate today = LocalDate.now();
        String bizKey = "SIGN_IN:" + user.getId() + ":" + today;
        PointsLedger existing = pointsLedgerMapper.selectOne(new LambdaQueryWrapper<PointsLedger>()
                .eq(PointsLedger::getBizKey, bizKey));
        if (existing != null) {
            return new CheckinResponse(0, user.getPoints(), true);
        }
        PointsLedger ledger = new PointsLedger();
        ledger.setUserId(user.getId());
        ledger.setAmount(10);
        ledger.setReason("SIGN_IN");
        ledger.setBizKey(bizKey);
        ledger.setCreatedAt(LocalDateTime.now());
        pointsLedgerMapper.insert(ledger);
        user.setPoints(user.getPoints() + 10);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return new CheckinResponse(10, user.getPoints(), false);
    }

    public PageResult<PointsLedgerView> pointsLedger(CurrentUser currentUser, long page, long size) {
        AppUser user = requireUser(currentUser.id());
        Page<PointsLedger> result = pointsLedgerMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<PointsLedger>()
                        .eq(PointsLedger::getUserId, user.getId())
                        .orderByDesc(PointsLedger::getId)
        );
        return new PageResult<>(
                result.getRecords().stream()
                        .map(ledger -> new PointsLedgerView(ledger.getId(), ledger.getAmount(), ledger.getReason(), ledger.getBizKey(), ledger.getCreatedAt()))
                        .toList(),
                page,
                size,
                result.getTotal()
        );
    }

    @Transactional
    public PasswordResetDevResponse requestPasswordReset(PasswordResetRequest request) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getEmail, normalizeEmail(request.email())));
        if (user == null) {
            return new PasswordResetDevResponse("EMAIL_DEV_SIMULATOR", "sent-if-user-exists", LocalDateTime.now().plusMinutes(20));
        }
        String token = "%06d".formatted(RANDOM.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(20);
        PasswordResetToken reset = new PasswordResetToken();
        reset.setUserId(user.getId());
        reset.setToken(token);
        reset.setExpiresAt(expiresAt);
        reset.setUsed(false);
        reset.setCreatedAt(LocalDateTime.now());
        resetTokenMapper.insert(reset);

        NotificationMessage message = new NotificationMessage();
        message.setUserId(user.getId());
        message.setChannel("STATION");
        message.setTitle("Password reset code");
        message.setBody("Your development reset code is " + token);
        message.setReadFlag(false);
        message.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(message);
        return new PasswordResetDevResponse("EMAIL_DEV_SIMULATOR", token, expiresAt);
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirm request) {
        PasswordResetToken reset = resetTokenMapper.selectOne(new LambdaQueryWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getToken, request.token())
                .eq(PasswordResetToken::getUsed, false)
                .gt(PasswordResetToken::getExpiresAt, LocalDateTime.now())
                .orderByDesc(PasswordResetToken::getId)
                .last("limit 1"));
        if (reset == null) {
            throw new BizException("RESET_TOKEN_INVALID", "Reset token is invalid or expired");
        }
        AppUser user = requireUser(reset.getUserId());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        reset.setUsed(true);
        resetTokenMapper.updateById(reset);
    }

    public PageResult<UserProfile> adminUsers(long page, long size, String keyword) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>().orderByDesc(AppUser::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AppUser::getEmail, keyword.trim()).or().like(AppUser::getUsername, keyword.trim()));
        }
        Page<AppUser> result = userMapper.selectPage(Page.of(page, size), wrapper);
        return new PageResult<>(result.getRecords().stream().map(this::profile).toList(), page, size, result.getTotal());
    }

    @Transactional
    public UserProfile adminUpdateUser(Long id, AdminUserUpdateRequest request) {
        AppUser user = requireUser(id);
        if (request.status() != null && Set.of("ACTIVE", "DISABLED").contains(request.status())) {
            user.setStatus(request.status());
        }
        if (request.roles() != null && !request.roles().isBlank()) {
            user.setRoles(request.roles().toUpperCase());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return profile(user);
    }

    private TokenResponse tokenFor(AppUser user) {
        Set<String> roles = roles(user);
        String token = jwtSupport.issue(user.getId(), roles, ACCESS_TOKEN_TTL_SECONDS);
        return new TokenResponse(token, ACCESS_TOKEN_TTL_SECONDS, profile(user), roles);
    }

    private UserProfile profile(AppUser user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getUsername(), roles(user), user.getStatus(), user.getPoints(), user.getVipUntil());
    }

    private Set<String> roles(AppUser user) {
        Set<String> roles = Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        if (user.getVipUntil() != null && user.getVipUntil().isAfter(LocalDateTime.now())) {
            roles.add("VIP");
        }
        return roles;
    }

    private AppUser requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException("UNAUTHORIZED", "Login required");
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        return user;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
