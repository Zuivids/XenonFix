-- The editable public pages and the shop's contact details: one JSON document each, holding all
-- three languages. The application fills in a missing page from resources/pages/*.json at startup.
CREATE TABLE page (
    page_key   VARCHAR(20) NOT NULL,
    content    LONGTEXT    NOT NULL CHECK (JSON_VALID(content)),
    updated_at DATETIME(6) NOT NULL,
    -- Empty for the starting content
    updated_by BIGINT      NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (page_key),
    CONSTRAINT fk_page_updated_by FOREIGN KEY (updated_by) REFERENCES admin_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Photos uploaded for the pages, so the ones never saved into a page can be deleted later
CREATE TABLE page_upload (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    path       VARCHAR(200) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_page_upload_path (path)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
