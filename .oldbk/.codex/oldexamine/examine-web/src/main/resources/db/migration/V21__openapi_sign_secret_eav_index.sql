-- G-6: 开放 API 签名模式所需的可逆加密 SK（AES-GCM，服务端验签用）
ALTER TABLE un_app_client_credential
    ADD COLUMN sign_secret_enc VARCHAR(512) NULL COMMENT 'SK 加密存储（签名模式验签；明文 SK 不落库）' AFTER secret_hash;

-- G-7: EAV 等值过滤 (model_id, field_code, value_text) 加速 EXISTS 子查询
ALTER TABLE un_module_record_data
    ADD KEY idx_module_record_data_eq (model_id, field_code, value_text(128), record_id);
