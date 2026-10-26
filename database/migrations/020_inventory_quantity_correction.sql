CREATE TABLE IF NOT EXISTS inventory_quantity_correction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    movement_id BIGINT NOT NULL,
    revision INT NOT NULL,
    previous_count INT NOT NULL,
    corrected_count INT NOT NULL,
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    verified_at DATETIME NOT NULL,
    performer_name VARCHAR(128) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    reference VARCHAR(128) NOT NULL DEFAULT '',
    original_hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    impact_json MEDIUMTEXT NOT NULL,
    impact_hash VARCHAR(64) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    correction_hash VARCHAR(64) NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME NOT NULL,
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_quantity_correction_revision (movement_id,revision),
    CONSTRAINT fk_quantity_correction_movement FOREIGN KEY (movement_id) REFERENCES inventory_movement(id),
    CONSTRAINT ck_quantity_correction_count CHECK (previous_count>=0 AND corrected_count>=0 AND previous_count<>corrected_count AND balance_before>=0 AND balance_after>=0),
    CONSTRAINT ck_quantity_correction_revision CHECK (revision BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE OR REPLACE VIEW inventory_effective_movement AS
SELECT m.*,
    CASE WHEN m.kind='LOSS' THEN COALESCE((SELECT c.corrected_count FROM inventory_loss_correction c WHERE c.movement_id=m.id ORDER BY c.revision DESC LIMIT 1),m.package_count)
         ELSE COALESCE((SELECT c.corrected_count FROM inventory_quantity_correction c WHERE c.movement_id=m.id ORDER BY c.revision DESC LIMIT 1),m.package_count) END AS effective_count,
    CASE WHEN m.kind='LOSS' THEN (SELECT MAX(c.recorded_at) FROM inventory_loss_correction c WHERE c.movement_id=m.id)
         ELSE (SELECT MAX(c.recorded_at) FROM inventory_quantity_correction c WHERE c.movement_id=m.id) END AS last_correction_at
FROM inventory_movement m;
