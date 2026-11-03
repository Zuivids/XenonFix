CREATE TABLE product (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    -- The address of the product page: same in all languages, and stable once published
    slug           VARCHAR(200)   NOT NULL,
    -- OEM codes: the same in every language, so not in product_translation
    part_number    VARCHAR(200)   NULL,
    price          DECIMAL(10, 2) NOT NULL,
    sale_price     DECIMAL(10, 2) NULL,
    stock_quantity INT            NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_slug (slug),
    KEY ix_product_status (status),
    CONSTRAINT ck_product_stock CHECK (stock_quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product_translation (
    product_id  BIGINT        NOT NULL,
    language    VARCHAR(2)    NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(5000) NULL,
    PRIMARY KEY (product_id, language),
    CONSTRAINT fk_product_translation_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- The files are on disk under the uploads folder; these are their paths from that folder.
CREATE TABLE product_image (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    product_id     BIGINT       NOT NULL,
    path           VARCHAR(255) NOT NULL,
    thumbnail_path VARCHAR(255) NOT NULL,
    width          INT          NOT NULL,
    height         INT          NOT NULL,
    -- The lowest comes first and is the product's main photo
    sort_order     INT          NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_product_image_product (product_id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
