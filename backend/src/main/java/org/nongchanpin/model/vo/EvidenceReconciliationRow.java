package org.nongchanpin.model.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class EvidenceReconciliationRow {
    private String kind,action,frozenHash,chainStatus,errorCode,traceCode,plotName,packingCode,reportNo;
    private String transactionStatus,receiptStatus;
    private Long recordId,packingId,itemId,reportId,txId,organizationId,cycleId;
    private Integer version;
    private LocalDateTime recordedAt,submittedAt;
    @JsonIgnore private LocalDateTime leaseUntil;
}
