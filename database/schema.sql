CREATE DATABASE IF NOT EXISTS agri_trace DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE agri_trace;

CREATE TABLE IF NOT EXISTS sys_organization (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  org_code VARCHAR(64) NOT NULL,
  org_name VARCHAR(128) NOT NULL,
  org_type VARCHAR(32) NOT NULL,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  address VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_organization_code (org_code),
  KEY idx_sys_organization_type (org_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  organization_id BIGINT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32),
  email VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_login_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  permissions JSON,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  product_name VARCHAR(128) NOT NULL,
  category VARCHAR(64) NOT NULL,
  variety VARCHAR(128),
  origin_place VARCHAR(255),
  description TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_code (product_code),
  KEY idx_product_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  organization_id BIGINT NOT NULL,
  batch_code VARCHAR(64) NOT NULL,
  production_area VARCHAR(255),
  plant_date DATE,
  harvest_date DATE,
  batch_metadata JSON,
  chain_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_batch_code (batch_code),
  KEY idx_product_batch_product (product_id),
  KEY idx_product_batch_chain_status (chain_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trace_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  trace_code_hash VARCHAR(64) NOT NULL,
  trace_code_encrypted VARCHAR(512),
  trace_code_plain VARCHAR(128),
  trace_url VARCHAR(512),
  qr_status VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
  nfc_status VARCHAR(32) NOT NULL DEFAULT 'UNBOUND',
  chain_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_trace_item_code_hash (trace_code_hash),
  UNIQUE KEY uk_trace_item_plain (trace_code_plain),
  KEY idx_trace_item_batch (batch_id),
  KEY idx_trace_item_chain_status (chain_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS nfc_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  uid_hash VARCHAR(64) NOT NULL,
  tag_hash VARCHAR(64),
  item_id BIGINT,
  bind_status VARCHAR(32) NOT NULL DEFAULT 'UNBOUND',
  bound_at DATETIME,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_nfc_tag_uid_hash (uid_hash),
  UNIQUE KEY uk_nfc_tag_active_item (item_id, status),
  KEY idx_nfc_tag_hash (tag_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trace_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  event_time DATETIME NOT NULL,
  location VARCHAR(255),
  operator_name VARCHAR(64),
  canonical_payload_hash VARCHAR(64) NOT NULL,
  event_hash VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  chain_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_trace_event_hash (event_hash),
  KEY idx_trace_event_item_time (item_id, event_time),
  KEY idx_trace_event_batch (batch_id),
  KEY idx_trace_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quality_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT,
  item_id BIGINT,
  report_no VARCHAR(64) NOT NULL,
  agency_name VARCHAR(128),
  result VARCHAR(32) NOT NULL,
  report_hash VARCHAR(64),
  file_url VARCHAR(512),
  checked_at DATETIME,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_quality_report_no (report_no),
  KEY idx_quality_report_item (item_id),
  KEY idx_quality_report_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iot_gateway (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  farm_id BIGINT NOT NULL,
  gateway_code VARCHAR(64) NOT NULL,
  gateway_name VARCHAR(128),
  mqtt_client_id VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_iot_gateway_code (gateway_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iot_device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  gateway_id BIGINT,
  farm_id BIGINT NOT NULL,
  gateway_code VARCHAR(64),
  device_code VARCHAR(64) NOT NULL,
  device_name VARCHAR(128),
  device_type VARCHAR(64),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_seen_at DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_iot_device_code (device_code),
  KEY idx_iot_device_gateway (gateway_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iot_sensor_reading (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  farm_id BIGINT NOT NULL,
  gateway_code VARCHAR(64) NOT NULL,
  device_code VARCHAR(64) NOT NULL,
  batch_id BIGINT,
  metric VARCHAR(64) NOT NULL,
  value DECIMAL(18,6) NOT NULL,
  unit VARCHAR(32),
  signature VARCHAR(256),
  reported_at DATETIME NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RAW',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_iot_reading_device_metric_time (device_code, metric, reported_at),
  KEY idx_iot_reading_batch (batch_id),
  KEY idx_iot_reading_gateway_time (gateway_code, reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iot_data_window (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  farm_id BIGINT NOT NULL,
  batch_id BIGINT,
  gateway_code VARCHAR(64),
  device_code VARCHAR(64) NOT NULL,
  metric VARCHAR(64) NOT NULL,
  window_start DATETIME NOT NULL,
  window_end DATETIME NOT NULL,
  min_value DECIMAL(18,6),
  max_value DECIMAL(18,6),
  avg_value DECIMAL(18,6),
  sample_count INT NOT NULL DEFAULT 0,
  data_hash VARCHAR(64) NOT NULL,
  chain_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_iot_window_hash (data_hash),
  KEY idx_iot_window_device_metric_time (device_code, metric, window_start),
  KEY idx_iot_window_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iot_alert (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  farm_id BIGINT NOT NULL,
  gateway_code VARCHAR(64),
  device_code VARCHAR(64) NOT NULL,
  metric VARCHAR(64) NOT NULL,
  alert_type VARCHAR(32) NOT NULL,
  level VARCHAR(16) NOT NULL DEFAULT 'WARN',
  value DECIMAL(18,6),
  threshold_min DECIMAL(18,6),
  threshold_max DECIMAL(18,6),
  message VARCHAR(255),
  occurred_at DATETIME NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_iot_alert_device_time (device_code, occurred_at),
  KEY idx_iot_alert_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chain_transaction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_type VARCHAR(64) NOT NULL,
  business_id BIGINT,
  business_id_hash VARCHAR(66),
  chain_status VARCHAR(32) NOT NULL,
  tx_hash VARCHAR(128),
  block_number BIGINT,
  receipt_status VARCHAR(64),
  contract_address VARCHAR(128),
  event_name VARCHAR(64),
  event_indexed_keys JSON,
  payload_hash VARCHAR(66),
  chain_operator VARCHAR(128),
  request_hash VARCHAR(64),
  error_message VARCHAR(512),
  submitted_at DATETIME,
  confirmed_at DATETIME,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chain_tx_hash (tx_hash),
  KEY idx_chain_tx_business (business_type, business_id),
  KEY idx_chain_tx_business_hash (business_type, business_id_hash),
  KEY idx_chain_tx_status (chain_status, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_type VARCHAR(32) NOT NULL,
  subject_id BIGINT,
  provider VARCHAR(64) NOT NULL DEFAULT 'disabled',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_session_type_subject (session_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  model VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_message_session_time (session_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS anchor_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_chain VARCHAR(64) NOT NULL DEFAULT 'FISCO_BCOS',
  anchor_chain VARCHAR(64) NOT NULL,
  merkle_root VARCHAR(64) NOT NULL,
  tx_hash VARCHAR(128),
  anchor_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_anchor_tx_hash (tx_hash),
  KEY idx_anchor_record_root (merkle_root)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 增量建表；不修改既有批次、设备、环境或链上记录。
CREATE TABLE IF NOT EXISTS farm_plot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  plot_code VARCHAR(64) NOT NULL,
  plot_name VARCHAR(128) NOT NULL,
  location VARCHAR(255) NOT NULL,
  area_mu DECIMAL(12,2) NOT NULL,
  manager_name VARCHAR(64) NOT NULL,
  notes TEXT,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_farm_plot_code (plot_code),
  CONSTRAINT fk_plot_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
  CONSTRAINT ck_plot_area CHECK (area_mu > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS planting_cycle (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  cycle_code VARCHAR(64) NOT NULL,
  product_name VARCHAR(128) NOT NULL,
  variety VARCHAR(128) NOT NULL,
  planted_on DATE NOT NULL,
  expected_harvest_on DATE,
  manager_name VARCHAR(64) NOT NULL,
  environment_notes TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  ended_on DATE,
  end_reason VARCHAR(1000),
  active_plot_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN plot_id ELSE NULL END) STORED,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_planting_cycle_code (cycle_code),
  UNIQUE KEY uk_planting_active_plot (active_plot_id),
  KEY idx_planting_plot_history (plot_id, planted_on),
  CONSTRAINT fk_cycle_plot FOREIGN KEY (plot_id) REFERENCES farm_plot(id),
  CONSTRAINT fk_cycle_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT ck_cycle_dates CHECK (expected_harvest_on IS NULL OR expected_harvest_on >= planted_on),
  CONSTRAINT ck_cycle_state CHECK ((status = 'ACTIVE' AND ended_on IS NULL AND end_reason IS NULL)
    OR (status = 'ENDED' AND ended_on >= planted_on AND end_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS growing_environment_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cycle_id BIGINT NOT NULL,
  metric VARCHAR(32) NOT NULL,
  min_value DECIMAL(18,6),
  max_value DECIMAL(18,6),
  unit VARCHAR(32) NOT NULL,
  expected_interval_minutes INT NOT NULL,
  basis VARCHAR(1000) NOT NULL,
  notes VARCHAR(2000),
  effective_from DATETIME(6) NOT NULL,
  effective_to DATETIME(6),
  created_by BIGINT NOT NULL,
  request_key VARCHAR(36) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  active_metric VARCHAR(96) GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL THEN CONCAT(cycle_id, ':', metric) ELSE NULL END) STORED,
  UNIQUE KEY uk_environment_rule_request (request_key),
  UNIQUE KEY uk_environment_active_metric (active_metric),
  KEY idx_environment_rule_time (cycle_id, metric, effective_from),
  CONSTRAINT ck_environment_range CHECK ((min_value IS NOT NULL OR max_value IS NOT NULL) AND (min_value IS NULL OR max_value IS NULL OR min_value <= max_value))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS environment_assessment (
  reading_id BIGINT PRIMARY KEY,
  cycle_id BIGINT NOT NULL,
  rule_id BIGINT,
  metric VARCHAR(32) NOT NULL,
  result VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_assessment_cycle (cycle_id, reading_id),
  CONSTRAINT fk_assessment_reading FOREIGN KEY (reading_id) REFERENCES iot_sensor_reading(id),
  CONSTRAINT fk_assessment_rule FOREIGN KEY (rule_id) REFERENCES growing_environment_rule(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS environment_case (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reading_id BIGINT NOT NULL,
  cycle_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  handled_by BIGINT,
  handled_at DATETIME(6),
  action_taken VARCHAR(2000),
  observation VARCHAR(2000),
  UNIQUE KEY uk_environment_case_reading (reading_id),
  KEY idx_environment_case_cycle (cycle_id, status, id),
  CONSTRAINT fk_case_assessment FOREIGN KEY (reading_id) REFERENCES environment_assessment(reading_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS farm_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cycle_id BIGINT NOT NULL,
  task_code VARCHAR(64) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  title VARCHAR(128) NOT NULL,
  planned_on DATE NOT NULL,
  assigned_to BIGINT NOT NULL,
  assignee_name VARCHAR(64) NOT NULL,
  instructions VARCHAR(2000),
  status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
  created_by BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  cancelled_by BIGINT,
  cancelled_at DATETIME(6),
  cancel_reason VARCHAR(1000),
  UNIQUE KEY uk_farm_task_code (task_code),
  KEY idx_farm_task_cycle (cycle_id, status, planned_on),
  CONSTRAINT fk_farm_task_cycle FOREIGN KEY (cycle_id) REFERENCES planting_cycle(id),
  CONSTRAINT fk_farm_task_assignee FOREIGN KEY (assigned_to) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS farm_task_execution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  performed_by BIGINT NOT NULL,
  performer_name VARCHAR(64) NOT NULL,
  performed_at DATETIME NOT NULL,
  input_name VARCHAR(128),
  quantity DECIMAL(18,6),
  unit VARCHAR(32),
  notes VARCHAR(2000) NOT NULL,
  evidence_reference VARCHAR(500),
  recorded_by BIGINT NOT NULL,
  recorded_name VARCHAR(64) NOT NULL,
  recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  request_key VARCHAR(36) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  UNIQUE KEY uk_farm_execution_task (task_id),
  UNIQUE KEY uk_farm_execution_request (request_key),
  CONSTRAINT fk_farm_execution_task FOREIGN KEY (task_id) REFERENCES farm_task(id),
  CONSTRAINT ck_farm_execution_quantity CHECK (quantity IS NULL OR quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS harvest_lot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cycle_id BIGINT NOT NULL,
    lot_code VARCHAR(64) NOT NULL UNIQUE,
    harvested_at DATETIME NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    unit VARCHAR(8) NOT NULL,
    weight_kg DECIMAL(12,3) NOT NULL,
    performer_name VARCHAR(128) NOT NULL,
    notes VARCHAR(2000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    recorded_by BIGINT NOT NULL,
    recorder_name VARCHAR(128) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_harvest_cycle FOREIGN KEY (cycle_id) REFERENCES planting_cycle(id),
    CONSTRAINT chk_harvest_weight CHECK (weight_kg > 0),
    INDEX idx_harvest_cycle (cycle_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS harvest_grade (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    harvest_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    grade_name VARCHAR(128) NOT NULL,
    weight_kg DECIMAL(12,3) NOT NULL,
    handled_at DATETIME NOT NULL,
    performer_name VARCHAR(128) NOT NULL,
    location VARCHAR(256) NOT NULL DEFAULT '',
    reason VARCHAR(1000) NOT NULL DEFAULT '',
    recorded_by BIGINT NOT NULL,
    recorder_name VARCHAR(128) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_grade_harvest FOREIGN KEY (harvest_id) REFERENCES harvest_lot(id),
    CONSTRAINT chk_grade_weight CHECK (weight_kg > 0),
    INDEX idx_grade_harvest (harvest_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS packing_lot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    grade_id BIGINT NOT NULL,
    packing_code VARCHAR(64) NOT NULL UNIQUE,
    packed_at DATETIME NOT NULL,
    package_count INT NOT NULL,
    package_unit VARCHAR(32) NOT NULL,
    net_weight_kg DECIMAL(12,3) NOT NULL,
    total_weight_kg DECIMAL(12,3) NOT NULL,
    performer_name VARCHAR(128) NOT NULL,
    location VARCHAR(256) NOT NULL,
    notes VARCHAR(2000) NOT NULL DEFAULT '',
    recorded_by BIGINT NOT NULL,
    recorder_name VARCHAR(128) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_packing_grade FOREIGN KEY (grade_id) REFERENCES harvest_grade(id),
    CONSTRAINT chk_packing_weight CHECK (package_count > 0 AND net_weight_kg > 0 AND total_weight_kg > 0),
    INDEX idx_packing_grade (grade_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS agent_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    source_text TEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    request_key VARCHAR(36) NOT NULL UNIQUE,
    mode VARCHAR(16) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_agent_report_source (owner_id,source_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_revision (
    report_id BIGINT NOT NULL,
    version INT NOT NULL,
    plan_json LONGTEXT NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (report_id,version),
    CONSTRAINT fk_agent_revision FOREIGN KEY (report_id) REFERENCES agent_report(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_confirmation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    version INT NOT NULL,
    scope_json TEXT NOT NULL,
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    confirmed_by BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    confirmed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_confirmation FOREIGN KEY (report_id,version) REFERENCES agent_revision(report_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_receipt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    confirmation_id BIGINT NOT NULL,
    operation_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    business_id BIGINT,
    message VARCHAR(1000) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_agent_receipt (confirmation_id,operation_id),
    CONSTRAINT fk_agent_receipt FOREIGN KEY (confirmation_id) REFERENCES agent_confirmation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_conversation_owner (owner_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_chat_turn (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    user_text TEXT NOT NULL,
    reply_text MEDIUMTEXT,
    evidence_json MEDIUMTEXT,
    report_id BIGINT,
    proposal_hash VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(500),
    attempt_token VARCHAR(36) NOT NULL,
    lease_until DATETIME NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at DATETIME(6),
    CONSTRAINT fk_chat_conversation FOREIGN KEY (conversation_id) REFERENCES agent_conversation(id),
    CONSTRAINT fk_chat_report FOREIGN KEY (report_id) REFERENCES agent_report(id),
    INDEX idx_chat_turn (conversation_id,id),
    INDEX idx_chat_proposal (conversation_id,proposal_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS item_dispatch_operation (
    request_key VARCHAR(36) PRIMARY KEY,
    dispatch_id BIGINT NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    recorded_by BIGINT NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_item_operation_dispatch FOREIGN KEY (dispatch_id) REFERENCES sales_dispatch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS item_dispatch_link (
    item_id BIGINT PRIMARY KEY,
    dispatch_id BIGINT NOT NULL,
    link_request_key VARCHAR(36) NOT NULL,
    linked_by BIGINT NOT NULL,
    linked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    receipt_event_id BIGINT,
    receipt_outcome VARCHAR(16),
    receipt_request_key VARCHAR(36),
    receipt_by BIGINT,
    receipt_at DATETIME(6),
    INDEX idx_item_dispatch (dispatch_id,item_id),
    CONSTRAINT ck_item_receipt CHECK (
        (receipt_outcome IS NULL AND receipt_event_id IS NULL AND receipt_request_key IS NULL AND receipt_by IS NULL AND receipt_at IS NULL)
        OR (receipt_outcome IS NOT NULL AND receipt_outcome IN ('ACCEPTED','REJECTED') AND receipt_event_id IS NOT NULL AND receipt_request_key IS NOT NULL AND receipt_by IS NOT NULL AND receipt_at IS NOT NULL)
    ),
    CONSTRAINT fk_item_link_item FOREIGN KEY (item_id) REFERENCES packing_trace_item(item_id),
    CONSTRAINT fk_item_link_dispatch FOREIGN KEY (dispatch_id) REFERENCES sales_dispatch(id),
    CONSTRAINT fk_item_link_operation FOREIGN KEY (link_request_key) REFERENCES item_dispatch_operation(request_key),
    CONSTRAINT fk_item_receipt_event FOREIGN KEY (receipt_event_id) REFERENCES transport_event(id),
    CONSTRAINT fk_item_receipt_operation FOREIGN KEY (receipt_request_key) REFERENCES item_dispatch_operation(request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
