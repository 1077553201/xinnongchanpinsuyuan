package org.nongchanpin.security;

import lombok.Data;

@Data
public class Actor {
    private Long id;
    private Long organizationId;
    private String username;
    private String displayName;
    private String role;

    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean canWrite() { return isAdmin() || "MANAGER".equals(role); }
}
