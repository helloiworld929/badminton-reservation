-- 已有数据库的论坛模块增量脚本。
-- 新部署仍直接使用 src/main/resources/schema.sql；已有 Docker 数据卷只需执行本文件一次。
USE badminton;

CREATE TABLE IF NOT EXISTS forum_posts (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT        NOT NULL COMMENT '发帖用户ID',
    title          VARCHAR(80)   NOT NULL COMMENT '标题',
    content        VARCHAR(2000) NOT NULL COMMENT '纯文本正文',
    category       VARCHAR(16)   NOT NULL COMMENT '固定帖子分类',
    status         VARCHAR(16)   DEFAULT 'normal' COMMENT 'normal/hidden/deleted',
    is_pinned      TINYINT(1)    DEFAULT 0 COMMENT '是否置顶',
    view_count     INT           DEFAULT 0 COMMENT '轻量访问次数',
    handled_by     BIGINT        COMMENT '最后处理管理员ID',
    handled_at     DATETIME      COMMENT '最后处理时间',
    created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_forum_post_user (user_id),
    INDEX idx_forum_post_status (status),
    INDEX idx_forum_post_category (category),
    INDEX idx_forum_post_order (is_pinned, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子表';

CREATE TABLE IF NOT EXISTS forum_post_images (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id        BIGINT       NOT NULL COMMENT '帖子ID',
    image_url      VARCHAR(512) NOT NULL COMMENT 'OSS图片URL',
    sort_order     INT          DEFAULT 0 COMMENT '展示顺序',
    status         VARCHAR(16)  DEFAULT 'normal' COMMENT 'normal/hidden/deleted',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_forum_image_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子图片表';

CREATE TABLE IF NOT EXISTS forum_replies (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id        BIGINT       NOT NULL COMMENT '帖子ID',
    user_id        BIGINT       NOT NULL COMMENT '回复用户ID',
    content        VARCHAR(500) NOT NULL COMMENT '纯文本回复',
    status         VARCHAR(16)  DEFAULT 'normal' COMMENT 'normal/hidden/deleted',
    handled_by     BIGINT       COMMENT '处理管理员ID',
    handled_at     DATETIME     COMMENT '处理时间',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_forum_reply_post (post_id),
    INDEX idx_forum_reply_user (user_id),
    INDEX idx_forum_reply_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛回复表';

CREATE TABLE IF NOT EXISTS forum_reports (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id    BIGINT       NOT NULL COMMENT '举报用户ID',
    target_type    VARCHAR(16)  NOT NULL COMMENT 'post/reply',
    target_id      BIGINT       NOT NULL COMMENT '被举报帖子或回复ID',
    reason         VARCHAR(32)  NOT NULL COMMENT '固定举报原因',
    status         VARCHAR(16)  DEFAULT 'pending' COMMENT 'pending/resolved/rejected',
    result         VARCHAR(200) COMMENT '管理员处理说明',
    handled_by     BIGINT       COMMENT '处理管理员ID',
    handled_at     DATETIME     COMMENT '处理时间',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_forum_report_status (status),
    INDEX idx_forum_report_target (target_type, target_id),
    INDEX idx_forum_reporter_target (reporter_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛举报表';
