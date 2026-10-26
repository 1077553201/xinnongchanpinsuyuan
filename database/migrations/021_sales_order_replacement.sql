CREATE TABLE IF NOT EXISTS sales_order_replacement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    original_order_id BIGINT NOT NULL UNIQUE,
    replacement_order_id BIGINT NOT NULL UNIQUE,
    reason VARCHAR(800) NOT NULL,
    impact_json MEDIUMTEXT NOT NULL,
    impact_hash VARCHAR(64) NOT NULL,
    record_hash VARCHAR(64) NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME NOT NULL,
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_sales_replacement_original FOREIGN KEY (original_order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_sales_replacement_new FOREIGN KEY (replacement_order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_sales_replacement_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    CONSTRAINT ck_sales_replacement_distinct CHECK (original_order_id<>replacement_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
