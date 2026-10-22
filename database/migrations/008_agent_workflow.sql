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
