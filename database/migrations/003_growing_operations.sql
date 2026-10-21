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
