SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS gray_anime DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE gray_anime;

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(120) NOT NULL UNIQUE,
  username VARCHAR(60) NOT NULL,
  password_hash VARCHAR(120) NOT NULL,
  roles VARCHAR(120) NOT NULL,
  status VARCHAR(20) NOT NULL,
  points INT NOT NULL DEFAULT 0,
  vip_until DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS password_reset_token (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token VARCHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  used TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  INDEX idx_reset_token (token),
  INDEX idx_reset_user (user_id)
);

CREATE TABLE IF NOT EXISTS points_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  amount INT NOT NULL,
  reason VARCHAR(50) NOT NULL,
  biz_key VARCHAR(120) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL,
  INDEX idx_points_user (user_id)
);

CREATE TABLE IF NOT EXISTS notification_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  channel VARCHAR(30) NOT NULL,
  title VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  read_flag TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  INDEX idx_notice_user (user_id)
);

CREATE TABLE IF NOT EXISTS work (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(160) NOT NULL,
  work_type VARCHAR(20) NOT NULL,
  author VARCHAR(100) NOT NULL,
  category VARCHAR(60) NOT NULL,
  description TEXT NULL,
  cover_url VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL,
  popularity INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_work_type_category (work_type, category),
  INDEX idx_work_title (title)
);

CREATE TABLE IF NOT EXISTS chapter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  work_id BIGINT NOT NULL,
  chapter_no INT NOT NULL,
  title VARCHAR(160) NOT NULL,
  free_flag TINYINT(1) NOT NULL DEFAULT 0,
  price_points INT NOT NULL DEFAULT 0,
  published_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_chapter_work_no (work_id, chapter_no),
  INDEX idx_chapter_work (work_id)
);

CREATE TABLE IF NOT EXISTS chapter_content (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  chapter_id BIGINT NOT NULL UNIQUE,
  content_text MEDIUMTEXT NULL,
  content_images TEXT NULL
);

CREATE TABLE IF NOT EXISTS chapter_entitlement (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  chapter_id BIGINT NOT NULL,
  source VARCHAR(30) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_entitlement_user_chapter (user_id, chapter_id)
);

CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(160) NOT NULL,
  product_type VARCHAR(30) NOT NULL,
  description TEXT NULL,
  cover_url VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL,
  limited_flag TINYINT(1) NOT NULL DEFAULT 0,
  sale_start_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_product_type (product_type)
);

CREATE TABLE IF NOT EXISTS sku (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  sku_name VARCHAR(100) NOT NULL,
  price_cents INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  INDEX idx_sku_product (product_id)
);

CREATE TABLE IF NOT EXISTS cart_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_cart_user_sku (user_id, sku_id)
);

CREATE TABLE IF NOT EXISTS inventory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku_id BIGINT NOT NULL UNIQUE,
  stock_available INT NOT NULL,
  stock_locked INT NOT NULL DEFAULT 0,
  limit_per_user INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_reservation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reservation_no VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  biz_key VARCHAR(160) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_reservation_user_sku (user_id, sku_id)
);

CREATE TABLE IF NOT EXISTS flash_sale (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku_id BIGINT NOT NULL,
  start_at DATETIME NOT NULL,
  end_at DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL,
  INDEX idx_flash_sku (sku_id)
);

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  order_type VARCHAR(30) NOT NULL,
  total_cents INT NOT NULL DEFAULT 0,
  total_points INT NOT NULL DEFAULT 0,
  status VARCHAR(30) NOT NULL,
  payment_no VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_orders_user (user_id),
  INDEX idx_orders_created (created_at),
  INDEX idx_orders_status_created (status, created_at)
);

CREATE TABLE IF NOT EXISTS order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  item_type VARCHAR(30) NOT NULL,
  ref_id BIGINT NULL,
  sku_id BIGINT NULL,
  title VARCHAR(160) NOT NULL,
  quantity INT NOT NULL,
  unit_price_cents INT NOT NULL DEFAULT 0,
  unit_points INT NOT NULL DEFAULT 0,
  reservation_no VARCHAR(64) NULL,
  INDEX idx_order_item_order (order_id)
);

CREATE TABLE IF NOT EXISTS payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(64) NOT NULL UNIQUE,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  amount_cents INT NOT NULL,
  channel VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at DATETIME NOT NULL,
  confirmed_at DATETIME NULL,
  INDEX idx_payment_order (order_no)
);

CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  aggregate_type VARCHAR(60) NOT NULL,
  aggregate_id VARCHAR(80) NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(30) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  published_at DATETIME NULL,
  INDEX idx_outbox_status (status, id)
);

CREATE TABLE IF NOT EXISTS import_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_type VARCHAR(40) NOT NULL,
  source_name VARCHAR(120) NOT NULL,
  status VARCHAR(30) NOT NULL,
  imported_works INT NOT NULL DEFAULT 0,
  imported_products INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  created_at DATETIME NOT NULL,
  finished_at DATETIME NULL,
  INDEX idx_import_created (created_at)
);

INSERT INTO work (id, title, work_type, author, category, description, cover_url, status, popularity, created_at, updated_at)
VALUES
  (1, '星轨书店的魔女', 'NOVEL', 'Lumen Circle', 'Fantasy', '在废弃轨道尽头经营书店的魔女，收集被遗忘的故事碎片。', 'https://picsum.photos/seed/gray-novel-a/900/1200', 'PUBLISHED', 932, NOW(), NOW()),
  (2, '雨港机械少女', 'MANGA', 'North Pier', 'Sci-Fi', '机械少女在雨港寻找失踪设计师的漫画分镜演示。', 'https://picsum.photos/seed/gray-manga-a/900/1200', 'PUBLISHED', 884, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  work_type = VALUES(work_type),
  author = VALUES(author),
  category = VALUES(category),
  description = VALUES(description),
  cover_url = VALUES(cover_url),
  status = VALUES(status),
  popularity = VALUES(popularity),
  updated_at = VALUES(updated_at);

INSERT INTO chapter (id, work_id, chapter_no, title, free_flag, price_points, published_at, created_at)
VALUES
  (1, 1, 1, '第一章 夜班列车', 1, 0, NOW(), NOW()),
  (2, 1, 2, '第二章 星砂契约', 0, 20, NOW(), NOW()),
  (3, 2, 1, '第1话 蓝色雨衣', 1, 0, NOW(), NOW()),
  (4, 2, 2, '第2话 生锈心跳', 0, 25, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  free_flag = VALUES(free_flag),
  price_points = VALUES(price_points),
  published_at = VALUES(published_at);

INSERT INTO chapter_content (chapter_id, content_text, content_images)
VALUES
  (1, '这是一段用于简历项目演示的原创模拟文本。夜班列车从星轨边缘驶来，书页在风里发光。', ''),
  (2, '付费章节示例：星砂契约被打开，魔女必须在黎明前完成交换。', ''),
  (3, '漫画分镜演示：蓝色雨衣、旧码头、启动核心。', ''),
  (4, '付费漫画分镜演示：维修仓、错位记忆、雨夜追逐。', '')
ON DUPLICATE KEY UPDATE
  content_text = VALUES(content_text),
  content_images = VALUES(content_images);

INSERT INTO product (id, title, product_type, description, cover_url, status, limited_flag, sale_start_at, created_at, updated_at)
VALUES
  (1, '《星轨书店的魔女》实体书限定版', 'BOOK', '首刷附星砂书签，VIP 9折。', 'https://picsum.photos/seed/gray-book-a/900/1200', 'ON_SALE', 0, NULL, NOW(), NOW()),
  (2, '雨港机械少女 亚克力立牌', 'GOODS', '限时开售，每人限购 1 件。', 'https://picsum.photos/seed/gray-stand-a/900/1200', 'ON_SALE', 1, DATE_ADD(NOW(), INTERVAL 2 HOUR), NOW(), NOW())
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  product_type = VALUES(product_type),
  description = VALUES(description),
  cover_url = VALUES(cover_url),
  status = VALUES(status),
  limited_flag = VALUES(limited_flag),
  sale_start_at = VALUES(sale_start_at),
  updated_at = VALUES(updated_at);

INSERT INTO sku (id, product_id, sku_name, price_cents, status)
VALUES
  (1, 1, '限定版', 6800, 'ACTIVE'),
  (2, 2, '标准款', 3900, 'ACTIVE')
ON DUPLICATE KEY UPDATE
  sku_name = VALUES(sku_name),
  price_cents = VALUES(price_cents),
  status = VALUES(status);

INSERT INTO inventory (sku_id, stock_available, stock_locked, limit_per_user, version, updated_at)
VALUES
  (1, 120, 0, 0, 0, NOW()),
  (2, 30, 0, 1, 0, NOW())
ON DUPLICATE KEY UPDATE stock_available = VALUES(stock_available), limit_per_user = VALUES(limit_per_user);
