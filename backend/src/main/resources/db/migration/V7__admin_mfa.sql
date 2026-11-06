-- Two-step login with an authenticator app. The key is encrypted with APP_MFA_KEY (MfaSecretCipher).
ALTER TABLE admin_user
    ADD COLUMN mfa_secret     VARBINARY(128) NULL AFTER password_change_required,
    ADD COLUMN mfa_enabled_at DATETIME(6)    NULL AFTER mfa_secret,
    -- The time step of the last code used, so a code cannot be used twice
    ADD COLUMN mfa_last_step  BIGINT         NULL AFTER mfa_enabled_at;

-- One-time backup codes, stored only as SHA-256 hashes
CREATE TABLE admin_recovery_code (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    admin_id   BIGINT      NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_admin_recovery_code_admin (admin_id),
    CONSTRAINT fk_admin_recovery_code_admin FOREIGN KEY (admin_id) REFERENCES admin_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
