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
