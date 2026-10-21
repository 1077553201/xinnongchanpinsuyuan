package org.nongchanpin.service;

import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.AccountMapper;
import org.nongchanpin.security.AccessContext;
import org.nongchanpin.security.Actor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AccountMapper accounts;
    public void record(String action, String type, Long id, String detail) {
        Actor actor = AccessContext.require();
        accounts.audit(actor.getId(), actor.getDisplayName(), actor.getOrganizationId(), action, type, id, detail);
    }
}
