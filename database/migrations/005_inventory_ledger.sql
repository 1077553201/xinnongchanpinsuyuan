CREATE TABLE IF NOT EXISTS inventory_warehouse (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    warehouse_code VARCHAR(64) NOT NULL UNIQUE,
    warehouse_name VARCHAR(128) NOT NULL,
    location VARCHAR(256) NOT NULL,
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_warehouse_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    INDEX idx_warehouse_org (organization_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_movement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    packing_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    movement_code VARCHAR(64) NOT NULL UNIQUE,
    kind VARCHAR(8) NOT NULL,
    package_count INT NOT NULL,
    package_unit VARCHAR(32) NOT NULL,
    weight_kg DECIMAL(12,3) NOT NULL,
    occurred_at DATETIME NOT NULL,
    performer_name VARCHAR(128) NOT NULL,
    destination VARCHAR(256) NOT NULL DEFAULT '',
    reason VARCHAR(1000) NOT NULL,
    reference VARCHAR(128) NOT NULL DEFAULT '',
    recorded_by BIGINT NOT NULL,
    recorder_name VARCHAR(128) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_movement_packing FOREIGN KEY (packing_id) REFERENCES packing_lot(id),
    CONSTRAINT fk_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES inventory_warehouse(id),
    CONSTRAINT chk_movement_count CHECK (package_count > 0 AND weight_kg > 0),
    CONSTRAINT chk_movement_kind CHECK (kind IN ('IN','OUT','LOSS')),
    INDEX idx_movement_source (packing_id,warehouse_id,occurred_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
