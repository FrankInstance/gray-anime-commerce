package com.gray.anime.user.infrastructure.maintenance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "demo.maintenance.cleanup-enabled", havingValue = "true")
class DemoInventoryCacheSynchronizer {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    DemoInventoryCacheSynchronizer(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    void refresh() {
        jdbcTemplate.query(
                "SELECT sku_id, stock_available FROM inventory",
                (RowCallbackHandler) resultSet -> redisTemplate.opsForValue().set(
                        "inventory:sku:" + resultSet.getLong("sku_id"),
                        Integer.toString(resultSet.getInt("stock_available"))
                )
        );
    }
}
