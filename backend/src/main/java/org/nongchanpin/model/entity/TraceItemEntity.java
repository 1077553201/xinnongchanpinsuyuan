package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TraceItemEntity {
    private Long packingId;
    private Long id;
    private Long batchId;
    private String traceCodeHash;
    private String traceCodeEncrypted;
    private String traceCodePlain;
    private String traceUrl;
    private String qrStatus;
    private String nfcStatus;
    private String chainStatus;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
