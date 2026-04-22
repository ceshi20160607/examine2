-- 从旧版 flow DDL 迁移：为 un_flow_record 增加运行时快照列 graph_json/form_json
-- 全新安装：直接使用最新的 docs/sql/07_flow_ddl.sql 即可，一般不需要执行本脚本。

SET NAMES utf8mb4;

-- 若列已存在会报错，请按实际库结构裁剪执行。
-- ALTER TABLE un_flow_record
--   ADD COLUMN graph_json JSON NOT NULL COMMENT '运行时流程图快照（模板→record 复制）' AFTER temp_ver_no,
--   ADD COLUMN form_json  JSON NULL COMMENT '运行时表单快照（可选）' AFTER graph_json;

