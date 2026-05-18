-- 手工库增量（与 Flyway V21 等价）
ALTER TABLE un_app_client_credential
    ADD COLUMN sign_secret_enc VARCHAR(512) NULL COMMENT 'SK 加密存储（签名模式验签）' AFTER secret_hash;

ALTER TABLE un_module_record_data
    ADD KEY idx_module_record_data_eq (model_id, field_code, value_text(128), record_id);
