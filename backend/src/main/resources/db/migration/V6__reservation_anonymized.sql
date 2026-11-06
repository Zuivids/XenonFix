-- A customer's details can be erased, on request or when the retention period ends. The
-- reservation itself stays, for the shop's records: number, product, amounts, status and dates.
ALTER TABLE reservation
    MODIFY customer_name  VARCHAR(100) NULL,
    MODIFY customer_phone VARCHAR(30)  NULL,
    MODIFY customer_email VARCHAR(254) NULL,
    ADD COLUMN anonymized_at DATETIME(6) NULL AFTER privacy_accepted_at,
    ADD KEY ix_reservation_status_updated (status, updated_at);
