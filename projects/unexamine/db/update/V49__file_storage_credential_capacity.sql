-- 对象存储访问密钥使用随机向量加密，密文长度不能受早期短引用字段限制。
ALTER TABLE `file_storage_backend`
  MODIFY COLUMN `credential_ref` TEXT NULL COMMENT '对象存储访问凭证密文，仅服务端解密，接口不得回传';
