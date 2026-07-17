USE gray_anime;

-- User-supplied demo artwork is stored locally under frontend/web/public/catalog.
-- The novel body below is original test copy and does not reproduce the linked third-party chapter.
INSERT INTO work (title, work_type, author, category, description, cover_url, status, popularity, created_at, updated_at)
SELECT '彻夜之歌', 'MANGA', '琴山', '都市 · 恋爱', '无法入睡的少年走进夜晚，在霓虹街巷中遇见改变日常的吸血鬼少女。', '/catalog/yofukashi-cover.png', 'PUBLISHED', 1218, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM work WHERE title = '彻夜之歌' AND work_type = 'MANGA');

UPDATE work
SET author = '琴山',
    category = '都市 · 恋爱',
    description = '无法入睡的少年走进夜晚，在霓虹街巷中遇见改变日常的吸血鬼少女。',
    cover_url = '/catalog/yofukashi-cover.png',
    status = 'PUBLISHED',
    popularity = 1218,
    updated_at = NOW()
WHERE title = '彻夜之歌' AND work_type = 'MANGA';

INSERT INTO work (title, work_type, author, category, description, cover_url, status, popularity, created_at, updated_at)
SELECT '欢迎来到实力至上主义的教室', 'NOVEL', '衣笠彰梧', '校园 · 智斗', '以实力衡量一切的校园里，学生们围绕班级评价、点数与选择展开竞争。', '/catalog/classroom-cover.png', 'PUBLISHED', 1365, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM work WHERE title = '欢迎来到实力至上主义的教室' AND work_type = 'NOVEL');

UPDATE work
SET author = '衣笠彰梧',
    category = '校园 · 智斗',
    description = '以实力衡量一切的校园里，学生们围绕班级评价、点数与选择展开竞争。',
    cover_url = '/catalog/classroom-cover.png',
    status = 'PUBLISHED',
    popularity = 1365,
    updated_at = NOW()
WHERE title = '欢迎来到实力至上主义的教室' AND work_type = 'NOVEL';

SET @yofukashi_work_id = (
  SELECT id FROM work WHERE title = '彻夜之歌' AND work_type = 'MANGA' ORDER BY id LIMIT 1
);
SET @classroom_work_id = (
  SELECT id FROM work WHERE title = '欢迎来到实力至上主义的教室' AND work_type = 'NOVEL' ORDER BY id LIMIT 1
);

INSERT INTO chapter (work_id, chapter_no, title, free_flag, price_points, published_at, created_at)
SELECT @yofukashi_work_id, 1, '第1话 夜色相遇', 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM chapter WHERE work_id = @yofukashi_work_id AND chapter_no = 1);

UPDATE chapter
SET title = '第1话 夜色相遇', free_flag = 1, price_points = 0, published_at = NOW()
WHERE work_id = @yofukashi_work_id AND chapter_no = 1;

INSERT INTO chapter (work_id, chapter_no, title, free_flag, price_points, published_at, created_at)
SELECT @classroom_work_id, 1, '第一章 欢迎来到梦幻般的校园生活', 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM chapter WHERE work_id = @classroom_work_id AND chapter_no = 1);

UPDATE chapter
SET title = '第一章 欢迎来到梦幻般的校园生活', free_flag = 1, price_points = 0, published_at = NOW()
WHERE work_id = @classroom_work_id AND chapter_no = 1;

SET @yofukashi_chapter_id = (
  SELECT id FROM chapter WHERE work_id = @yofukashi_work_id AND chapter_no = 1 LIMIT 1
);
SET @classroom_chapter_id = (
  SELECT id FROM chapter WHERE work_id = @classroom_work_id AND chapter_no = 1 LIMIT 1
);

INSERT INTO chapter_content (chapter_id, content_text, content_images)
VALUES
  (@yofukashi_chapter_id, '', '/catalog/yofukashi-chapter-1.png'),
  (@classroom_chapter_id, CONCAT(
    '四月的风穿过校门时，站在公告栏前的新生都不约而同地放慢了脚步。这里的建筑、制服和迎新标语看起来与普通学校没有区别，只有入学说明中反复出现的“评价”二字，提醒每个人接下来的生活并不简单。',
    '\n\n教室里的座位已经排好，桌面上放着学生手册和一台只允许连接校园网络的终端。班主任没有立刻讲课，而是让所有人查看刚刚到账的个人点数。数字远比预想中慷慨，教室里很快响起压低的欢呼声。',
    '\n\n有人已经计划放学后的购物，有人怀疑这份优待另有条件，也有人只是安静地翻完规则。手册写得十分客气：点数可以在校园内代替现金，但每月发放数量会根据学生表现重新计算。至于“表现”具体包含什么，没有任何解释。',
    REPEAT('\n\n午休前，班里进行了第一次临时讨论。靠窗的学生提出先统计大家擅长的科目，坐在前排的人却认为现在没有必要紧张。看似随意的意见逐渐分成几组，每个人都在观察别人，也在判断谁值得合作。\n\n走廊尽头的电子屏滚动播放社团招募和生活通知。画面切换的一瞬间，屏幕上短暂出现了班级评价排行，又很快被新的公告覆盖。注意到这一幕的人不多，但原本轻松的气氛已经悄悄发生变化。\n\n下午的课程结束后，校园商店挤满了拿到点数的新生。货架上的商品种类齐全，价格却高低悬殊。少年没有急着购买，只选了一瓶水，然后站在结算台旁观察终端扣款的提示。每一次消费都被完整记录，像是一场尚未公布规则的测验。\n\n回到宿舍时，学生手册推送了当天最后一条通知：下个月的点数并不保证与本月相同。简短的一句话让群聊迅速热闹起来。有人抱怨学校故弄玄虚，有人开始计算剩余点数，也有人删除了刚刚列好的购物清单。\n\n窗外的教学楼逐层熄灯，操场边仍有几支社团在训练。少年把手册放回桌面，重新整理今天听到的每一句话。学校给予的自由并非没有代价，只是代价暂时还没有标在任何商品上。', 8),
    '\n\n午夜前，班级终端再次亮起。新的页面只显示班级名称和一个尚未变化的数字。少年看了片刻便关掉屏幕。他知道，真正的入学说明直到此刻才刚刚开始。'
  ), '')
ON DUPLICATE KEY UPDATE
  content_text = VALUES(content_text),
  content_images = VALUES(content_images);
