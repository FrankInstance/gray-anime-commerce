package com.gray.anime.user.infrastructure.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gray.anime.user.domain.AppUser;
import com.gray.anime.user.infrastructure.mapper.AppUserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserDataSeeder implements ApplicationRunner {
    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserDataSeeder(AppUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("admin@gray.test", "Admin@123456", "Gray Admin", "USER,VIP,ADMIN,SUPER_ADMIN", 300);
        seed("demo@gray.test", "Demo@123456", "Demo Reader", "USER", 80);
    }

    private void seed(String email, String password, String username, String roles, int points) {
        AppUser existed = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getEmail, email));
        if (existed != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoles(roles);
        user.setStatus("ACTIVE");
        user.setPoints(points);
        user.setVipUntil(roles.contains("VIP") ? now.plusMonths(12) : null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
    }
}
