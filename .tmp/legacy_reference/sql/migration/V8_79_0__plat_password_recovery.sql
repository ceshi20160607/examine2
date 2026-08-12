CREATE TABLE un_plat_password_recovery_token (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_password_recovery_token_hash (token_hash),
    KEY idx_plat_password_recovery_account (account_id, status, expires_at),
    CONSTRAINT fk_plat_password_recovery_account
        FOREIGN KEY (account_id) REFERENCES un_plat_account (id),
    CONSTRAINT ck_plat_password_recovery_status
        CHECK (status IN ('ACTIVE', 'USED', 'REVOKED')),
    CONSTRAINT ck_plat_password_recovery_expiry
        CHECK (expires_at > requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
