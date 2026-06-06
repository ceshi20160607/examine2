-- Existing databases baselined at V22 may have skipped V21 while the entity
-- already writes sign_secret_enc during OpenAPI client creation.
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
