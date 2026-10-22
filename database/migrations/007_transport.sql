CREATE TABLE IF NOT EXISTS transport_shipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dispatch_id BIGINT NOT NULL UNIQUE,
    shipment_code VARCHAR(64) NOT NULL UNIQUE,
    carrier VARCHAR(128) NOT NULL,
    tracking_reference VARCHAR(128) NOT NULL DEFAULT '',
    driver_name VARCHAR(128) NOT NULL,
    driver_contact VARCHAR(128) NOT NULL DEFAULT '',
    recipient VARCHAR(128) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    recorded_by BIGINT NOT NULL,
    recorder_name VARCHAR(128) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_transport_dispatch FOREIGN KEY (dispatch_id) REFERENCES sales_dispatch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transport_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shipment_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    occurred_at DATETIME NOT NULL,
    location VARCHAR(256) NOT NULL,
    performer_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    reference VARCHAR(128) NOT NULL DEFAULT '',
    accepted_count INT,
    rejected_count INT,
    recorded_by BIGINT NOT NULL,
    recorder_name VARCHAR(128) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_transport_event FOREIGN KEY (shipment_id) REFERENCES transport_shipment(id),
    INDEX idx_transport_event (shipment_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
