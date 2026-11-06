-- For admins only: never shown to customers or sent in emails
ALTER TABLE reservation ADD COLUMN admin_note VARCHAR(2000) NULL AFTER comment;
