CREATE TABLE reservation (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    product_id          BIGINT         NOT NULL,
    -- What was reserved, as it was then: later product changes must not change a reservation.
    -- The name is the Latvian one, because the admin panel is in Latvian.
    product_name        VARCHAR(200)   NOT NULL,
    part_number         VARCHAR(200)   NULL,
    unit_price          DECIMAL(10, 2) NOT NULL,
    quantity            INT            NOT NULL,
    customer_name       VARCHAR(100)   NOT NULL,
    customer_phone      VARCHAR(30)    NOT NULL,
    customer_email      VARCHAR(254)   NOT NULL,
    comment             VARCHAR(1000)  NULL,
    -- The language the customer used, for the messages they get
    language            VARCHAR(2)     NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    privacy_accepted_at DATETIME(6)    NOT NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_reservation_status_created (status, created_at),
    CONSTRAINT fk_reservation_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
