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
