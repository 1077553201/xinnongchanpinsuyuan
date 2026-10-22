CREATE TABLE IF NOT EXISTS sales_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    order_code VARCHAR(64) NOT NULL UNIQUE,
    ordered_on DATE NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    customer_contact VARCHAR(128) NOT NULL DEFAULT '',
    delivery_address VARCHAR(500) NOT NULL,
    notes VARCHAR(2000) NOT NULL DEFAULT '',
    total_amount DECIMAL(20,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    recorded_by BIGINT NOT NULL,
    recorder_name VARCHAR(128) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_by BIGINT,
    closed_at DATETIME(6),
    close_reason VARCHAR(1000),
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_sales_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    INDEX idx_sales_org (organization_id,status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sales_order_line (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    packing_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    packing_code VARCHAR(64) NOT NULL,
    warehouse_name VARCHAR(128) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    variety VARCHAR(128) NOT NULL,
    package_unit VARCHAR(32) NOT NULL,
    net_weight_kg DECIMAL(12,3) NOT NULL,
    package_count INT NOT NULL,
    unit_price DECIMAL(11,2) NOT NULL,
    line_amount DECIMAL(20,2) NOT NULL,
    CONSTRAINT fk_sales_line_order FOREIGN KEY (order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_sales_line_packing FOREIGN KEY (packing_id) REFERENCES packing_lot(id),
    CONSTRAINT fk_sales_line_warehouse FOREIGN KEY (warehouse_id) REFERENCES inventory_warehouse(id),
    CONSTRAINT chk_sales_line_count CHECK (package_count > 0 AND unit_price >= 0),
    UNIQUE KEY uk_sales_line_source (order_id,packing_id,warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sales_dispatch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    line_id BIGINT NOT NULL,
    movement_id BIGINT NOT NULL UNIQUE,
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_dispatch_line FOREIGN KEY (line_id) REFERENCES sales_order_line(id),
    CONSTRAINT fk_dispatch_movement FOREIGN KEY (movement_id) REFERENCES inventory_movement(id),
    INDEX idx_dispatch_line (line_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
