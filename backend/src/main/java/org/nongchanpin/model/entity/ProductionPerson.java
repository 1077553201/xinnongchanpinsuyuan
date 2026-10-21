package org.nongchanpin.model.entity;

import lombok.Data;

@Data
public class ProductionPerson {
    private Long id;
    private Long organizationId;
    private String username;
    private String displayName;
}
