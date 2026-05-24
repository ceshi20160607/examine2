-- 适用：旧库 baseline 到 V22 导致 Flyway V21 被跳过，但开放应用创建已写 sign_secret_enc。
SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'un_app_client_credential'
    AND column_name = 'sign_secret_enc'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE un_app_client_credential ADD COLUMN sign_secret_enc VARCHAR(512) NULL COMMENT ''SK encrypted for signature verification'' AFTER secret_hash',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
