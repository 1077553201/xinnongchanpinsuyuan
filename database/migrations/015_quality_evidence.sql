CREATE TABLE IF NOT EXISTS source_quality_revision (
    previous_report_id BIGINT PRIMARY KEY,
    replacement_report_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_quality_revision_replacement (replacement_report_id),
    CONSTRAINT ck_quality_revision_order CHECK (replacement_report_id>previous_report_id),
    CONSTRAINT fk_quality_revision_previous FOREIGN KEY (previous_report_id) REFERENCES source_quality_report(id),
    CONSTRAINT fk_quality_revision_replacement FOREIGN KEY (replacement_report_id) REFERENCES source_quality_report(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quality_item_evidence (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    version INT NOT NULL,
    manifest_json MEDIUMTEXT NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64),
    event_id BIGINT NOT NULL,
    chain_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
    tx_id BIGINT,
    error_code VARCHAR(64),
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_token VARCHAR(36),
    lease_until DATETIME,
    UNIQUE KEY uk_quality_item_version (report_id,item_id,version),
    UNIQUE KEY uk_quality_item_event (event_id),
    CONSTRAINT ck_quality_item_version CHECK (version BETWEEN 1 AND 20),
    CONSTRAINT ck_quality_item_manifest CHECK (JSON_VALID(manifest_json)),
    CONSTRAINT fk_quality_item_report FOREIGN KEY (report_id) REFERENCES source_quality_report(id),
    CONSTRAINT fk_quality_item_item FOREIGN KEY (item_id) REFERENCES packing_trace_item(item_id),
    CONSTRAINT fk_quality_item_event FOREIGN KEY (event_id) REFERENCES trace_event(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
