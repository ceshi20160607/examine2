-- 一个制品可配合多个配置版本发布；版本号仍唯一，摘要只用于查询和校验。
ALTER TABLE `un_ops_release`
  DROP INDEX `uk_ops_release_hash`,
  ADD KEY `idx_ops_release_hash` (`artifact_hash`);
