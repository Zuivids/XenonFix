-- Shop-wide values that admins will be able to change later.
CREATE TABLE app_setting (
    setting_key   VARCHAR(100)  NOT NULL,
    setting_value VARCHAR(1000) NOT NULL,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO app_setting (setting_key, setting_value) VALUES ('shop_legal_name', 'SIA Elekanga');
