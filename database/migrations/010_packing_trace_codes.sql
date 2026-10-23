CREATE TABLE IF NOT EXISTS packing_trace_batch (
    packing_id BIGINT PRIMARY KEY,
    batch_id BIGINT NOT NULL UNIQUE,
    source_json MEDIUMTEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_packing_source_json CHECK (JSON_VALID(source_json)),
    CONSTRAINT fk_packing_trace_batch_packing FOREIGN KEY (packing_id) REFERENCES packing_lot(id),
    CONSTRAINT fk_packing_trace_batch_batch FOREIGN KEY (batch_id) REFERENCES product_batch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS packing_trace_issue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    packing_id BIGINT NOT NULL,
    code_count INT NOT NULL,
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    recorded_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_packing_trace_issue_count CHECK (code_count BETWEEN 1 AND 100),
    CONSTRAINT fk_packing_trace_issue_packing FOREIGN KEY (packing_id) REFERENCES packing_lot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS packing_trace_item (
    item_id BIGINT PRIMARY KEY,
    packing_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    ordinal BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_packing_trace_ordinal (packing_id,ordinal),
    INDEX idx_packing_trace_issue (issue_id),
    CONSTRAINT fk_packing_trace_item_item FOREIGN KEY (item_id) REFERENCES trace_item(id),
    CONSTRAINT fk_packing_trace_item_packing FOREIGN KEY (packing_id) REFERENCES packing_lot(id),
    CONSTRAINT fk_packing_trace_item_issue FOREIGN KEY (issue_id) REFERENCES packing_trace_issue(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
