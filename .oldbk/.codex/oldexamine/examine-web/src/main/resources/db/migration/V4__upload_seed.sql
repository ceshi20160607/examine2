-- Baseline: upload seed (from docs/sql/04_upload_seed.sql)
SET NAMES utf8mb4;

INSERT IGNORE INTO un_upload_storage_config (
  id,
  system_id,
  tenant_id,
  storage_type,
  local_root_path,
  local_public_base_url,
  endpoint,
  region,
  bucket,
  base_path,
  public_base_url,
  param_json,
  status,
  remark,
  create_user_id,
  update_user_id,
  create_time,
  update_time
) VALUES (
  0,
  0,
  0,
  'local',
  'D:\\data\\uploads',
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  1,
  '默认本地存储配置（可按环境修改）',
  NULL,
  NULL,
  NOW(3),
  NOW(3)
);

