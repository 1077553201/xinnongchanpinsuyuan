package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PackingNfcOperation {
    private Long id;
    private Long packingId;
    private Long itemId;
    private Long tagId;
    private String operation;
    private String payloadJson;
    private String payloadHash;
    private String privateReason;
    private String chainStatus;
    private Long txId;
    private String errorCode;
    private Long recordedBy;
    private LocalDateTime recordedAt;
    private String leaseToken;
    private LocalDateTime leaseUntil;
}
