ALTER TABLE chain_transaction
    ADD COLUMN receipt_verified_at DATETIME(6) NULL,
    ADD COLUMN receipt_verification_hash VARCHAR(64) NULL;
