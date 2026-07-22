-- 预约、场地和活动数据完整性迁移
-- 执行顺序：先检查下方查询结果；确认没有孤儿数据后，再执行 ALTER TABLE。

USE badminton;

-- 任一查询返回记录时，不要继续添加外键；应先人工确认并修复数据归属。
SELECT r.id AS reservation_id, r.user_id
FROM reservations r
LEFT JOIN users u ON u.id = r.user_id
WHERE u.id IS NULL;

SELECT r.id AS reservation_id, r.court_id
FROM reservations r
LEFT JOIN courts c ON c.id = r.court_id
WHERE c.id IS NULL;

SELECT s.id AS signup_id, s.activity_id, s.user_id
FROM activity_signups s
LEFT JOIN activities a ON a.id = s.activity_id
LEFT JOIN users u ON u.id = s.user_id
WHERE a.id IS NULL OR u.id IS NULL;

ALTER TABLE courts
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记' AFTER status;

ALTER TABLE activities
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记' AFTER image_url;

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_reservations_court
        FOREIGN KEY (court_id) REFERENCES courts(id) ON DELETE RESTRICT;

ALTER TABLE activity_signups
    ADD CONSTRAINT fk_activity_signups_activity
        FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_activity_signups_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
