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
