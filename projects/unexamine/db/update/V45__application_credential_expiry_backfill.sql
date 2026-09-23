-- 为旧版本签发且没有失效时间的应用凭证补齐一年有效期。
-- 只更新空值，保留原生效时间、撤销状态、密钥版本和历史调用关联。
UPDATE `app_credential`
SET `expires_at` = DATE_ADD(COALESCE(`valid_from`, `created_at`, CURRENT_TIMESTAMP(3)), INTERVAL 365 DAY)
WHERE `expires_at` IS NULL;
