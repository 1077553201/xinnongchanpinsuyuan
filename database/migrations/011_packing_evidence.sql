CREATE TABLE IF NOT EXISTS packing_evidence (
    packing_id BIGINT PRIMARY KEY,
    manifest_json MEDIUMTEXT NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    batch_payload MEDIUMTEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_token VARCHAR(36),
    lease_until DATETIME,
    CONSTRAINT ck_packing_manifest_json CHECK (JSON_VALID(manifest_json)),
    CONSTRAINT ck_packing_batch_payload CHECK (JSON_VALID(batch_payload)),
    CONSTRAINT fk_evidence_packing FOREIGN KEY (packing_id) REFERENCES packing_lot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS packing_evidence_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    packing_id BIGINT NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    payload_hash VARCHAR(66) NOT NULL,
    chain_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    tx_id BIGINT,
    error_code VARCHAR(64),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_packing_evidence_job (business_type,business_id),
    INDEX idx_evidence_job_packing (packing_id,id),
    CONSTRAINT ck_packing_job_payload CHECK (JSON_VALID(payload_json)),
    CONSTRAINT fk_evidence_job_packing FOREIGN KEY (packing_id) REFERENCES packing_lot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
