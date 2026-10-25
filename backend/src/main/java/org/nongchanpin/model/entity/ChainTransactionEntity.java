package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChainTransactionEntity {
    private Long id;
    private String businessType;
    private Long businessId;
    private String businessIdHash;
    private String chainStatus;
    private String txHash;
    private Long blockNumber;
    private String receiptStatus;
    private String contractAddress;
    private String eventName;
    private String eventIndexedKeys;
    private String payloadHash;
    private String chainOperator;
    private String requestHash;
    private String errorMessage;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime receiptVerifiedAt;
    private String receiptVerificationHash;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
