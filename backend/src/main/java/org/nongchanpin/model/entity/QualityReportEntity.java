package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QualityReportEntity {
    private Long id;
    private Long batchId;
    private Long itemId;
    private String reportNo;
    private String agencyName;
    private String result;
    private String reportHash;
    private String fileUrl;
    private LocalDateTime checkedAt;
    private String status;
}
