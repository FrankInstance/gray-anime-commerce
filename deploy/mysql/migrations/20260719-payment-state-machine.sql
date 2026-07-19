SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS gray_add_column_if_missing;
DELIMITER //
CREATE PROCEDURE gray_add_column_if_missing(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @gray_column_sql = CONCAT(
      'ALTER TABLE `', REPLACE(table_name_value, '`', '``'),
      '` ADD COLUMN ', column_definition_value
    );
    PREPARE gray_column_statement FROM @gray_column_sql;
    EXECUTE gray_column_statement;
    DEALLOCATE PREPARE gray_column_statement;
  END IF;
END//
DELIMITER ;

CALL gray_add_column_if_missing('orders', 'fulfillment_status',
  'fulfillment_status VARCHAR(30) NOT NULL DEFAULT ''NOT_REQUIRED'' AFTER status');
CALL gray_add_column_if_missing('orders', 'cancel_reason',
  'cancel_reason VARCHAR(60) NULL AFTER payment_no');
CALL gray_add_column_if_missing('orders', 'paid_at',
  'paid_at DATETIME NULL AFTER cancel_reason');
CALL gray_add_column_if_missing('orders', 'cancelled_at',
  'cancelled_at DATETIME NULL AFTER paid_at');

CALL gray_add_column_if_missing('payment', 'provider_session_id',
  'provider_session_id VARCHAR(160) NULL AFTER status');
CALL gray_add_column_if_missing('payment', 'session_expires_at',
  'session_expires_at DATETIME NULL AFTER provider_session_id');
CALL gray_add_column_if_missing('payment', 'failure_code',
  'failure_code VARCHAR(80) NULL AFTER session_expires_at');
CALL gray_add_column_if_missing('payment', 'attempt_count',
  'attempt_count INT NOT NULL DEFAULT 0 AFTER failure_code');
CALL gray_add_column_if_missing('payment', 'updated_at',
  'updated_at DATETIME NULL AFTER created_at');

UPDATE payment SET updated_at = COALESCE(updated_at, created_at);
UPDATE payment SET status = 'CREATED' WHERE status = 'PENDING' AND channel = 'UNASSIGNED';
UPDATE payment SET attempt_count = 1 WHERE status = 'PENDING' AND channel <> 'UNASSIGNED' AND attempt_count = 0;
ALTER TABLE payment MODIFY COLUMN updated_at DATETIME NOT NULL;

UPDATE orders
SET fulfillment_status = CASE
  WHEN status = 'PAID' AND order_type = 'CHAPTER' THEN 'NOT_REQUIRED'
  WHEN status = 'PAID' THEN 'COMPLETED'
  WHEN status = 'PENDING_PAYMENT' THEN 'PENDING'
  ELSE 'NOT_REQUIRED'
END
WHERE fulfillment_status = 'NOT_REQUIRED'
  AND status IN ('PAID', 'PENDING_PAYMENT');

UPDATE orders order_row
JOIN payment payment_row ON payment_row.payment_no = order_row.payment_no
SET order_row.paid_at = COALESCE(order_row.paid_at, payment_row.confirmed_at)
WHERE order_row.status = 'PAID';

UPDATE orders
SET cancelled_at = COALESCE(cancelled_at, updated_at)
WHERE status = 'CANCELLED';

CALL gray_add_column_if_missing('outbox_event', 'event_id',
  'event_id CHAR(36) NULL AFTER id');
CALL gray_add_column_if_missing('outbox_event', 'producer',
  'producer VARCHAR(80) NULL AFTER event_id');
CALL gray_add_column_if_missing('outbox_event', 'routing_key',
  'routing_key VARCHAR(120) NULL AFTER event_type');
CALL gray_add_column_if_missing('outbox_event', 'available_at',
  'available_at DATETIME NULL AFTER retry_count');
CALL gray_add_column_if_missing('outbox_event', 'last_error',
  'last_error VARCHAR(160) NULL AFTER available_at');

UPDATE outbox_event
SET event_id = COALESCE(event_id, UUID()),
    producer = COALESCE(producer, 'legacy'),
    routing_key = COALESCE(routing_key, event_type),
    available_at = COALESCE(available_at, created_at),
    status = CASE WHEN status IN ('NEW', 'RETRY', 'PUBLISHING') THEN 'DEAD' ELSE status END;

ALTER TABLE outbox_event
  MODIFY COLUMN event_id CHAR(36) NOT NULL,
  MODIFY COLUMN producer VARCHAR(80) NOT NULL,
  MODIFY COLUMN routing_key VARCHAR(120) NOT NULL,
  MODIFY COLUMN available_at DATETIME NOT NULL;

DROP PROCEDURE gray_add_column_if_missing;

DROP PROCEDURE IF EXISTS gray_add_index_if_missing;
DELIMITER //
CREATE PROCEDURE gray_add_index_if_missing(
  IN table_name_value VARCHAR(64),
  IN index_name_value VARCHAR(64),
  IN create_statement_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND index_name = index_name_value
  ) THEN
    SET @gray_index_sql = create_statement_value;
    PREPARE gray_index_statement FROM @gray_index_sql;
    EXECUTE gray_index_statement;
    DEALLOCATE PREPARE gray_index_statement;
  END IF;
END//
DELIMITER ;

CALL gray_add_index_if_missing('outbox_event', 'uk_outbox_event_id',
  'CREATE UNIQUE INDEX uk_outbox_event_id ON outbox_event (event_id)');
CALL gray_add_index_if_missing('outbox_event', 'idx_outbox_producer_status',
  'CREATE INDEX idx_outbox_producer_status ON outbox_event (producer, status, available_at, id)');
DROP PROCEDURE gray_add_index_if_missing;

CREATE TABLE IF NOT EXISTS inbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  consumer VARCHAR(100) NOT NULL,
  event_id CHAR(36) NOT NULL,
  processed_at DATETIME NOT NULL,
  UNIQUE KEY uk_inbox_consumer_event (consumer, event_id)
);

CREATE TABLE IF NOT EXISTS payment_transition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(64) NOT NULL,
  from_status VARCHAR(30) NULL,
  to_status VARCHAR(30) NOT NULL,
  trigger_type VARCHAR(60) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  trace_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_payment_transition_key (idempotency_key),
  INDEX idx_payment_transition_no (payment_no, id)
);
