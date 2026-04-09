-- upload 模块默认配置（DML）
-- 默认：本地存储（local），供 systemId=0 / tenantId=0 使用（平台默认）
-- 说明：如需为具体 system/tenant 定制，请新增记录并在 un_upload_file.storage_config_id 指向对应配置。

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
  'D:\\\\data\\\\uploads',
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

