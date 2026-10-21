CREATE TABLE IF NOT EXISTS operation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NOT NULL,
  actor_name VARCHAR(64) NOT NULL,
  organization_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  detail VARCHAR(2000),
  create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_audit_target (target_type, target_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS device_ownership (
  device_id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  assigned_by BIGINT NOT NULL,
  assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ownership_device FOREIGN KEY (device_id) REFERENCES iot_device(id),
  CONSTRAINT fk_ownership_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS monitoring_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  plot_id BIGINT NOT NULL,
  cycle_id BIGINT NOT NULL,
  gateway_code VARCHAR(64) NOT NULL,
  farm_id BIGINT NOT NULL,
  started_at DATETIME(6) NOT NULL,
  ended_at DATETIME(6),
  created_by BIGINT NOT NULL,
  ended_by BIGINT,
  end_reason VARCHAR(1000),
  active_device_id BIGINT GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN device_id ELSE NULL END) STORED,
  UNIQUE KEY uk_monitoring_active_device (active_device_id),
  KEY idx_monitoring_history (device_id, started_at, ended_at),
  KEY idx_monitoring_cycle (cycle_id, id),
  CONSTRAINT fk_binding_device FOREIGN KEY (device_id) REFERENCES iot_device(id),
  CONSTRAINT fk_binding_plot FOREIGN KEY (plot_id) REFERENCES farm_plot(id),
  CONSTRAINT fk_binding_cycle FOREIGN KEY (cycle_id) REFERENCES planting_cycle(id),
  CONSTRAINT ck_binding_time CHECK (ended_at IS NULL OR ended_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reading_provenance (
  reading_id BIGINT PRIMARY KEY,
  binding_id BIGINT,
  plot_id BIGINT,
  cycle_id BIGINT,
  resolution VARCHAR(32) NOT NULL,
  reason VARCHAR(128) NOT NULL,
  create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_reading_cycle (cycle_id, reading_id),
  CONSTRAINT fk_provenance_reading FOREIGN KEY (reading_id) REFERENCES iot_sensor_reading(id),
  CONSTRAINT fk_provenance_binding FOREIGN KEY (binding_id) REFERENCES monitoring_binding(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS window_provenance (
  window_id BIGINT PRIMARY KEY,
  binding_id BIGINT NOT NULL,
  plot_id BIGINT NOT NULL,
  cycle_id BIGINT NOT NULL,
  KEY idx_window_cycle (cycle_id, window_id),
  CONSTRAINT fk_window_provenance FOREIGN KEY (window_id) REFERENCES iot_data_window(id),
  CONSTRAINT fk_window_binding FOREIGN KEY (binding_id) REFERENCES monitoring_binding(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_role (role_code, role_name, status) VALUES
  ('ADMIN', '系统管理员', 'ACTIVE'), ('MANAGER', '生产人员', 'ACTIVE'), ('VIEWER', '只读人员', 'ACTIVE')
ON DUPLICATE KEY UPDATE role_code = VALUES(role_code);
