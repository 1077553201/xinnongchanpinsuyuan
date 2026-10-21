package org.nongchanpin.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class AccountEntity {
    private Long id;
    private Long organizationId;
    private String username;
    private String displayName;
    private String status;
    @JsonIgnore private String passwordHash;
}
