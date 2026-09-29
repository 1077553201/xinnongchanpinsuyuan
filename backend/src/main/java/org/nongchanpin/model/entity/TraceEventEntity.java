package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TraceEventEntity {
    private Long id;
    private Long itemId;
    private Long batchId;
    private String eventType;
    private LocalDateTime eventTime;
    private String location;
    private String operatorName;
    private String canonicalPayloadHash;
    private String eventHash;
    private String payload;
    private String chainStatus;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
