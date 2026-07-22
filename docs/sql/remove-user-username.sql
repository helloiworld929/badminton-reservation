-- 移除用户用户名，并将手机号设为唯一登录标识。
-- 执行顺序：先检查下方查询结果；确认手机号完整且无重复后，再执行 ALTER TABLE。

USE badminton;

-- 任一查询返回记录时，不要继续修改表结构；应先为用户补齐或更正手机号。
SELECT id, nickname
FROM users
WHERE phone IS NULL OR phone = '';

SELECT phone, COUNT(*) AS user_count
FROM users
WHERE phone IS NOT NULL AND phone != ''
GROUP BY phone
HAVING COUNT(*) > 1;

-- 历史数据允许年龄为空；统一按原字段默认值回填后再收紧非空约束。
SELECT id, nickname
FROM users
WHERE age IS NULL;

UPDATE users
SET age = 18
WHERE age IS NULL;

ALTER TABLE users
    DROP INDEX uk_username,
    DROP COLUMN username,
    MODIFY COLUMN age INT NOT NULL DEFAULT 18 COMMENT '年龄',
    MODIFY COLUMN phone VARCHAR(16) NOT NULL COMMENT '手机号',
    ADD UNIQUE KEY uk_phone (phone);
