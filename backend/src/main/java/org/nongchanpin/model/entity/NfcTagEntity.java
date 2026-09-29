package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NfcTagEntity {
    private Long id;
    private String uidHash;
    private String tagHash;
    private Long itemId;
    private String bindStatus;
    private LocalDateTime boundAt;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
