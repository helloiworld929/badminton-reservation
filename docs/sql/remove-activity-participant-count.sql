-- 已有数据库删除活动报名“参与人数”字段的一次性迁移脚本。
-- 先部署不再读写 participant_count 的新版应用，再执行本脚本。
USE badminton;

ALTER TABLE activity_signups
    DROP COLUMN participant_count;
