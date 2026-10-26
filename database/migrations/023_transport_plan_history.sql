CREATE TABLE IF NOT EXISTS transport_plan_origin (
    shipment_id BIGINT PRIMARY KEY,
    package_count INT NOT NULL,
    package_unit VARCHAR(32) NOT NULL,
    correction_recorded_at DATETIME,
    CONSTRAINT fk_plan_origin_shipment FOREIGN KEY (shipment_id) REFERENCES transport_shipment(id),
    CONSTRAINT ck_plan_origin_count CHECK (package_count>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transport_plan_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shipment_id BIGINT NOT NULL,
    revision INT NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(800) NOT NULL,
    shipment_code VARCHAR(64) UNIQUE,
    carrier VARCHAR(128),
    tracking_reference VARCHAR(128),
    driver_name VARCHAR(128),
    driver_contact VARCHAR(128),
    notes VARCHAR(1000),
    package_count INT,
    package_unit VARCHAR(32),
    correction_recorded_at DATETIME,
    impact_json MEDIUMTEXT NOT NULL,
    impact_hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    action_hash VARCHAR(64) NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME NOT NULL,
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_plan_action_revision (shipment_id,revision),
    CONSTRAINT fk_plan_action_shipment FOREIGN KEY (shipment_id) REFERENCES transport_shipment(id),
    CONSTRAINT ck_plan_action_revision CHECK (revision BETWEEN 1 AND 100),
    CONSTRAINT ck_plan_action_kind CHECK (action IN ('CANCEL','REPLACE')),
    CONSTRAINT ck_plan_action_quantity CHECK ((action='CANCEL' AND package_count IS NULL) OR (action='REPLACE' AND package_count>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transport_code_registry (
    shipment_code VARCHAR(64) PRIMARY KEY,
    shipment_id BIGINT NOT NULL,
    CONSTRAINT fk_transport_code_shipment FOREIGN KEY (shipment_id) REFERENCES transport_shipment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO transport_plan_origin(shipment_id,package_count,package_unit,correction_recorded_at)
SELECT s.id,m.effective_count,m.package_unit,m.last_correction_at FROM transport_shipment s JOIN sales_dispatch d ON d.id=s.dispatch_id JOIN inventory_effective_movement m ON m.id=d.movement_id
WHERE NOT EXISTS (SELECT 1 FROM transport_plan_origin p WHERE p.shipment_id=s.id);

INSERT INTO transport_code_registry(shipment_code,shipment_id)
SELECT s.shipment_code,s.id FROM transport_shipment s
WHERE NOT EXISTS (SELECT 1 FROM transport_code_registry r WHERE r.shipment_code=s.shipment_code AND r.shipment_id=s.id);

CREATE OR REPLACE VIEW transport_current_plan AS
SELECT s.id,s.dispatch_id,COALESCE(a.shipment_code,s.shipment_code) AS shipment_code,
    COALESCE(a.carrier,s.carrier) AS carrier,COALESCE(a.tracking_reference,s.tracking_reference) AS tracking_reference,
    COALESCE(a.driver_name,s.driver_name) AS driver_name,COALESCE(a.driver_contact,s.driver_contact) AS driver_contact,
    s.recipient,s.delivery_address,COALESCE(a.notes,s.notes) AS notes,s.status,s.recorded_by,s.recorder_name,s.recorded_at,s.request_key,s.request_hash,
    COALESCE(a.package_count,p.package_count) AS package_count,COALESCE(a.package_unit,p.package_unit) AS package_unit,
    CASE WHEN a.id IS NULL THEN p.correction_recorded_at ELSE a.correction_recorded_at END AS correction_recorded_at,
    COALESCE(a.revision,0) AS plan_revision,COALESCE(a.recorded_at,s.recorded_at) AS plan_recorded_at
FROM transport_shipment s JOIN transport_plan_origin p ON p.shipment_id=s.id
LEFT JOIN transport_plan_action a ON a.id=(SELECT h.id FROM transport_plan_action h WHERE h.shipment_id=s.id AND h.action='REPLACE' ORDER BY h.revision DESC LIMIT 1);
