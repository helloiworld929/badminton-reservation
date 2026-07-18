-- ============================================
-- 羽毛球场地预约系统 — 数据库初始化脚本
-- 版本: v3.0 (精简版)
-- ============================================

CREATE DATABASE IF NOT EXISTS badminton DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE badminton;

SET NAMES utf8mb4;

-- ============================================
-- 1. 用户表
-- ============================================
DROP TABLE IF EXISTS operation_logs;
DROP TABLE IF EXISTS activity_signups;
DROP TABLE IF EXISTS activities;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS courts;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    nickname       VARCHAR(64)  NOT NULL COMMENT '昵称',
    gender         VARCHAR(4)   DEFAULT '男' COMMENT '性别',
    age            INT          DEFAULT 18 COMMENT '年龄',
    username       VARCHAR(64)  NOT NULL COMMENT '用户名',
    password       VARCHAR(128) NOT NULL COMMENT '密码',
    phone          VARCHAR(16)  COMMENT '手机号',
    avatar         VARCHAR(256) COMMENT '头像URL',
    status         VARCHAR(16)  DEFAULT 'active' COMMENT 'active/restricted',
    role           VARCHAR(16)  DEFAULT 'user' COMMENT 'user/admin',
    noshow_count   INT          DEFAULT 0 COMMENT '累计爽约次数',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 2. 场地表
-- ============================================
CREATE TABLE courts (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(64)  NOT NULL COMMENT '场地名称',
    status         INT          DEFAULT 0 COMMENT '0=正常 1=锁定 2=维护中',
    remark         VARCHAR(128) COMMENT '备注',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地表';

-- ============================================
-- 3. 预约表
-- ============================================
CREATE TABLE reservations (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT        NOT NULL COMMENT 'FK -> users.id',
    court_id          BIGINT        NOT NULL COMMENT 'FK -> courts.id',
    reserve_date      DATE          NOT NULL COMMENT '预约日期',
    start_time        INT           NOT NULL COMMENT '开始小时（8-20）',
    end_time          INT           NOT NULL COMMENT '结束小时（start+1）',
    status            VARCHAR(16)   DEFAULT 'unverified' COMMENT 'unverified/verified/noshow/cancelled',
    verification_code VARCHAR(8)    COMMENT '6位核销码',
    verified_at       DATETIME      COMMENT '核销时间',
    verified_by       BIGINT        COMMENT '核销操作员ID',
    created_at        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reservation_user (user_id),
    INDEX idx_reservation_court (court_id),
    INDEX idx_reservation_slot (reserve_date, start_time),
    INDEX idx_reservation_status (status),
    UNIQUE KEY uk_user_court_slot (user_id, court_id, reserve_date, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约表';

-- ============================================
-- 4. 活动表
-- ============================================
CREATE TABLE activities (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(128) NOT NULL COMMENT '活动标题',
    activity_time  VARCHAR(64)  COMMENT '活动时间',
    location       VARCHAR(128) COMMENT '活动地点',
    description    VARCHAR(500) COMMENT '活动描述',
    image_url      VARCHAR(256) COMMENT '活动图片URL'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动表';

-- ============================================
-- 5. 活动报名表
-- ============================================
CREATE TABLE activity_signups (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id       BIGINT NOT NULL COMMENT 'FK -> activities.id',
    user_id           BIGINT NOT NULL COMMENT 'FK -> users.id',
    name              VARCHAR(64)  NOT NULL COMMENT '联系人姓名',
    phone             VARCHAR(16)  NOT NULL COMMENT '联系电话',
    participant_count INT    NOT NULL DEFAULT 1 COMMENT '参与人数',
    INDEX idx_signup_activity (activity_id),
    INDEX idx_signup_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名表';

-- ============================================
-- 6. 操作日志表
-- ============================================
CREATE TABLE operation_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id  BIGINT       COMMENT '关联预约ID',
    user_id         BIGINT       COMMENT '关联用户ID',
    operator_id     BIGINT       COMMENT '操作人ID（管理员）',
    action          VARCHAR(32)  NOT NULL COMMENT 'create/cancel/verify/noshow',
    detail          VARCHAR(500) COMMENT '操作详情',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_oplog_reservation (reservation_id),
    INDEX idx_oplog_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================
-- 种子数据
-- ============================================

-- 场地（4个）
INSERT INTO courts (name, status, remark) VALUES
('1号场', 0, NULL),
('2号场', 0, NULL),
('3号场', 0, NULL),
('4号场', 0, NULL);

-- 示例活动
INSERT INTO activities (title, activity_time, location, description, image_url) VALUES
('周末羽毛球友谊赛',   '2026-07-10 14:00', '体育馆A馆', '欢迎各位球友参加，水平不限，重在参与！丰厚奖品等你来拿！',       'https://my-study-javaweb.oss-cn-beijing.aliyuncs.com/activity/2026/06/30/25156fb79f634f96a3eae5b60315f4f3.jpg'),
('羽毛球基础教学课',   '2026-07-15 09:00', '体育馆B馆', '专业教练指导，适合零基础学员，提供训练用球。限20人报名。',         'https://my-study-javaweb.oss-cn-beijing.aliyuncs.com/activity/2026/06/30/2d54e3645ee14390a3892a96bd5bd14f.jpg'),
('双打挑战赛',         '2026-07-20 18:00', '体育馆A馆', '双打淘汰赛制，自由组队或随机匹配，前三名有奖！',                 'https://my-study-javaweb.oss-cn-beijing.aliyuncs.com/activity/2026/06/30/345d5384934b42dd8e93e57d074cca10.jpg'),
('青少年羽毛球夏令营', '2026-08-01 08:00', '体育馆C馆', '为期5天的封闭式训练，年龄8-16岁，包含体能训练和技巧提升。',       'https://my-study-javaweb.oss-cn-beijing.aliyuncs.com/activity/2026/06/30/3e91310ef900475db570b0261574c7d6.jpg');
