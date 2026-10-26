package org.nongchanpin.model.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OrganizationEntity {
    private Long id;
    private String orgCode;
    private String orgName;
    private String orgType;
    private String contactName;
    private String contactPhone;
    private String address;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
