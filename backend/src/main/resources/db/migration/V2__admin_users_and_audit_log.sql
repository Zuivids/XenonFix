CREATE TABLE admin_user (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    email                    VARCHAR(320) NOT NULL,
    password_hash            VARCHAR(255) NOT NULL,
    display_name             VARCHAR(100) NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    -- Set when someone else chose the password; cleared when the admin sets their own
    password_change_required BOOLEAN      NOT NULL,
    failed_login_count       INT          NOT NULL DEFAULT 0,
    locked_until             DATETIME(6)  NULL,
    -- Sessions remember this from login; raising it ends them on their next request
    security_version         BIGINT       NOT NULL DEFAULT 0,
    last_login_at            DATETIME(6)  NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Append-only: the application never updates or deletes these rows.
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6)  NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    -- Who did it; empty when nobody is logged in, such as a login with an unknown email
    admin_id    BIGINT       NULL,
    -- What it was about, for example ADMIN and an id. Later also products and reservations.
    target_type VARCHAR(50)  NULL,
    target_id   VARCHAR(100) NULL,
    ip_address  VARCHAR(45)  NULL,
    details     VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY ix_audit_log_occurred_at (occurred_at),
    CONSTRAINT fk_audit_log_admin FOREIGN KEY (admin_id) REFERENCES admin_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
