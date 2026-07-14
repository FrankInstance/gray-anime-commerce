package com.gray.anime.user.infrastructure.maintenance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@Profile("demo")
@ConditionalOnProperty(name = "demo.maintenance.cleanup-enabled", havingValue = "true")
class DemoDataCleanupRepository {
    private static final String JOB_NAME = "demo-user-data-cleanup";
    private static final String NON_ADMIN_USER = "FIND_IN_SET('ADMIN', u.roles) = 0 "
            + "AND FIND_IN_SET('SUPER_ADMIN', u.roles) = 0 AND u.created_at <= ?";

    private final JdbcTemplate jdbcTemplate;

    DemoDataCleanupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void initializeSchedule() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS demo_maintenance_job (
                  job_name VARCHAR(80) PRIMARY KEY,
                  status VARCHAR(20) NOT NULL,
                  next_run_at DATETIME NOT NULL,
                  run_started_at DATETIME NULL,
                  lease_until DATETIME NULL,
                  last_success_at DATETIME NULL,
                  last_error VARCHAR(500) NULL,
                  updated_at DATETIME NOT NULL
                )
                """);
        jdbcTemplate.update("""
                INSERT IGNORE INTO demo_maintenance_job
                  (job_name, status, next_run_at, updated_at)
                VALUES (?, 'WAITING', DATE_ADD(CURRENT_TIMESTAMP(), INTERVAL 6 MONTH), CURRENT_TIMESTAMP())
                """, JOB_NAME);
    }

    Optional<LocalDateTime> claimDueRun() {
        int claimed = jdbcTemplate.update("""
                UPDATE demo_maintenance_job
                SET status = 'RUNNING',
                    run_started_at = CURRENT_TIMESTAMP(),
                    lease_until = DATE_ADD(CURRENT_TIMESTAMP(), INTERVAL 30 MINUTE),
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP()
                WHERE job_name = ?
                  AND next_run_at <= CURRENT_TIMESTAMP()
                  AND (status IN ('WAITING', 'FAILED')
                       OR (status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP()))
                """, JOB_NAME);
        if (claimed == 0) {
            return Optional.empty();
        }
        LocalDateTime startedAt = jdbcTemplate.queryForObject(
                "SELECT run_started_at FROM demo_maintenance_job WHERE job_name = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toLocalDateTime(),
                JOB_NAME
        );
        return Optional.ofNullable(startedAt);
    }

    @Transactional
    DemoDataCleanupSummary purgeAndComplete(LocalDateTime cutoff) {
        Timestamp cutoffTimestamp = Timestamp.valueOf(cutoff);
        restoreInventory(cutoffTimestamp);

        deleteByUser("password_reset_token", cutoffTimestamp);
        deleteByUser("auth_session", cutoffTimestamp);
        deleteByUser("points_ledger", cutoffTimestamp);
        deleteByUser("notification_message", cutoffTimestamp);
        deleteByUser("chapter_entitlement", cutoffTimestamp);
        deleteByUser("user_bookshelf", cutoffTimestamp);
        deleteByUser("reading_progress", cutoffTimestamp);
        deleteByUser("cart_item", cutoffTimestamp);
        deleteByUser("stock_reservation", cutoffTimestamp);

        jdbcTemplate.update("""
                DELETE oi FROM order_item oi
                JOIN orders o ON o.id = oi.order_id
                JOIN app_user u ON u.id = o.user_id
                WHERE """ + NON_ADMIN_USER, cutoffTimestamp);
        jdbcTemplate.update("""
                DELETE event FROM outbox_event event
                LEFT JOIN orders o
                  ON event.aggregate_type = 'Order' AND event.aggregate_id = o.order_no
                LEFT JOIN payment p
                  ON event.aggregate_type = 'Payment' AND event.aggregate_id = p.payment_no
                JOIN app_user u ON u.id = COALESCE(o.user_id, p.user_id)
                WHERE """ + NON_ADMIN_USER, cutoffTimestamp);
        int paymentsDeleted = jdbcTemplate.update("""
                DELETE p FROM payment p
                JOIN app_user u ON u.id = p.user_id
                WHERE """ + NON_ADMIN_USER, cutoffTimestamp);
        int ordersDeleted = jdbcTemplate.update("""
                DELETE o FROM orders o
                JOIN app_user u ON u.id = o.user_id
                WHERE """ + NON_ADMIN_USER, cutoffTimestamp);

        int usersDeleted = jdbcTemplate.update(
                "DELETE u FROM app_user u WHERE " + NON_ADMIN_USER,
                cutoffTimestamp
        );
        jdbcTemplate.update("""
                UPDATE demo_maintenance_job
                SET status = 'WAITING',
                    next_run_at = DATE_ADD(CURRENT_TIMESTAMP(), INTERVAL 6 MONTH),
                    run_started_at = NULL,
                    lease_until = NULL,
                    last_success_at = CURRENT_TIMESTAMP(),
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP()
                WHERE job_name = ? AND status = 'RUNNING'
                """, JOB_NAME);
        return new DemoDataCleanupSummary(usersDeleted, ordersDeleted, paymentsDeleted);
    }

    void markFailed(String message) {
        String safeMessage = message == null || message.isBlank() ? "Unknown cleanup failure" : message;
        jdbcTemplate.update("""
                UPDATE demo_maintenance_job
                SET status = 'FAILED',
                    lease_until = NULL,
                    last_error = ?,
                    updated_at = CURRENT_TIMESTAMP()
                WHERE job_name = ? AND status = 'RUNNING'
                """, safeMessage.substring(0, Math.min(safeMessage.length(), 500)), JOB_NAME);
    }

    private void restoreInventory(Timestamp cutoff) {
        jdbcTemplate.update("""
                UPDATE inventory i
                JOIN (
                  SELECT sr.sku_id,
                         SUM(CASE WHEN sr.status IN ('LOCKED', 'CONFIRMED') THEN sr.quantity ELSE 0 END) AS available_to_restore,
                         SUM(CASE WHEN sr.status = 'LOCKED' THEN sr.quantity ELSE 0 END) AS locked_to_restore
                  FROM stock_reservation sr
                  JOIN app_user u ON u.id = sr.user_id
                  WHERE """ + NON_ADMIN_USER + """
                  GROUP BY sr.sku_id
                ) restored ON restored.sku_id = i.sku_id
                SET i.stock_available = i.stock_available + restored.available_to_restore,
                    i.stock_locked = GREATEST(i.stock_locked - restored.locked_to_restore, 0),
                    i.version = i.version + 1,
                    i.updated_at = CURRENT_TIMESTAMP()
                """, cutoff);
    }

    private void deleteByUser(String table, Timestamp cutoff) {
        jdbcTemplate.update(
                "DELETE target FROM " + table + " target "
                        + "JOIN app_user u ON u.id = target.user_id WHERE " + NON_ADMIN_USER,
                cutoff
        );
    }
}
