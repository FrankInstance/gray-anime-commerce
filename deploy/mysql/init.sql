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

CREATE TABLE IF NOT EXISTS auth_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_auth_session_user (user_id),
  INDEX idx_auth_session_expiry (expires_at)
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

CREATE TABLE IF NOT EXISTS user_bookshelf (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  work_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_bookshelf_user_work (user_id, work_id),
  INDEX idx_bookshelf_user_updated (user_id, updated_at)
);

CREATE TABLE IF NOT EXISTS reading_progress (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  work_id BIGINT NOT NULL,
  chapter_id BIGINT NOT NULL,
  chapter_no INT NOT NULL,
  chapter_title VARCHAR(160) NOT NULL,
  progress_percent INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_progress_user_work (user_id, work_id),
  INDEX idx_progress_user_updated (user_id, updated_at)
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
  fulfillment_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED',
  payment_no VARCHAR(64) NULL,
  cancel_reason VARCHAR(60) NULL,
  paid_at DATETIME NULL,
  cancelled_at DATETIME NULL,
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
  provider_session_id VARCHAR(160) NULL,
  session_expires_at DATETIME NULL,
  failure_code VARCHAR(80) NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  confirmed_at DATETIME NULL,
  INDEX idx_payment_order (order_no)
);

CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id CHAR(36) NOT NULL UNIQUE,
  producer VARCHAR(80) NOT NULL,
  aggregate_type VARCHAR(60) NOT NULL,
  aggregate_id VARCHAR(80) NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  routing_key VARCHAR(120) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(30) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  available_at DATETIME NOT NULL,
  last_error VARCHAR(160) NULL,
  created_at DATETIME NOT NULL,
  published_at DATETIME NULL,
  INDEX idx_outbox_status (producer, status, available_at, id)
);

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
  (1, CONCAT(
    '夜班列车从星轨边缘驶来时，站台上只有一盏坏掉的蓝灯。灯泡忽明忽暗，把铁轨照成一条发亮的裂缝，像是有人把夜色轻轻撕开。',
    '\n\n魔女把旧书箱放在脚边，箱扣上挂着一枚银色铃铛。每当风从隧道深处吹来，铃铛就会发出很轻的响声，像纸页翻动，又像远处有人在喊她的名字。',
    REPEAT('\n\n列车没有司机，也没有乘客，车厢里却亮着一排温热的灯。她沿着过道往前走，座位上散落着不同年代的车票：雨港的、沙丘镇的、还有一张写着星砂书店的旧票根。每一张票背后都写着一句没说完的话，墨水被夜露泡开，边缘泛出淡淡的蓝色。\n\n她把票根夹进书页，听见车窗外传来细小的敲击声。那不是雨，是星尘撞在玻璃上的声音。轨道两侧的废楼缓慢后退，屋顶长满发光的苔藓，广告牌上的霓虹字只剩几个断续的笔画。她认出其中一个牌子，许多年前，那里曾经是城市里最大的书店。\n\n列车越过第三座高架桥时，广播突然响起。沙哑的女声提醒乘客保管好自己的故事，下一站会回收所有无人认领的记忆。魔女抬起头，看到车厢尽头站着一个穿校服的女孩，女孩怀里抱着一本没有封面的书，书脊上拴着一截红线。\n\n女孩说，她想找回书的结尾。魔女没有立刻回答，只是打开箱子，把里面的空白书签一张张摊在桌面上。每一张书签都在微微发光，像等待被写下名字的星星。', 20),
    '\n\n列车终于停在废弃终点站。魔女合上书箱，铃铛响了一声。她知道，今晚的第一位客人已经到了，而这本书的故事，才刚刚开始。'
  ), ''),
  (2, CONCAT(
    '星砂契约被打开，纸面浮起细小的光点。魔女必须在黎明前完成交换，否则所有被她收留的故事都会重新散进夜色里。',
    REPEAT('\n\n契约的第一条写着：借来的结局，必须用真正的愿望归还。她把指尖按在印章上，听见远处列车再次鸣笛。女孩站在书店门口，红线从书脊垂到地面，另一端却消失在城市尽头。\n\n雨港的风从破窗吹进来，吹乱柜台上的账本。账本里夹着许多旧名字，有些被划掉，有些旁边画着小小的月亮。魔女知道，那些名字不是顾客，而是曾经差点失去自己的读者。\n\n当星砂落进茶杯时，杯底映出另一个夜晚。女孩在雨中奔跑，怀里的书被水打湿，字迹一点点浮上来。她终于明白，所谓结尾并不是最后一页，而是有人愿意继续读下去的地方。', 15),
    '\n\n天色发白前，契约安静地合上。书店外的轨道亮了一瞬，像有人在远方点燃新的站灯。'
  ), ''),
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
