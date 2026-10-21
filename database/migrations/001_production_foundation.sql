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
