package org.nongchanpin.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.OrganizationMapper;
import org.nongchanpin.model.dto.OrganizationRequest;
import org.nongchanpin.model.entity.OrganizationEntity;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class OrganizationService {
    private final OrganizationMapper organizations;
    private final AuditService audit;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> list(String search, String status, int page) {
        AccessContext.admin();
        String term = search == null ? "" : search.trim();
        if (term.length() > 128 || !Set.of("ALL", "ACTIVE", "DISABLED", "DELETED").contains(status) || page < 1) {
            throw new IllegalArgumentException("请核对机构搜索、状态和页码。");
        }
        long total = organizations.count(term, status);
        long current = Math.min((long) page, Math.max(1, (total + 19) / 20));
        return Map.of("rows", organizations.list(term, status, 20, (current - 1) * 20), "total", total, "page", current, "pageSize", 20);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> detail(Long id) {
        AccessContext.admin();
        OrganizationEntity row = required(id, false);
        List<Map<String, Object>> dependencies = organizations.dependencies(id);
        return Map.of("organization", row, "dependencies", dependencies, "canDelete", !"DELETED".equals(row.getStatus())
                && !id.equals(AccessContext.require().getOrganizationId()) && empty(dependencies));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrganizationEntity create(@Valid OrganizationRequest request) {
        AccessContext.admin();
        OrganizationEntity row = values(request);
        organizations.insert(row);
        audit.record("ORGANIZATION_CREATE", "ORGANIZATION", row.getId(), row.getOrgCode());
        return organizations.get(row.getId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrganizationEntity update(Long id, @Valid OrganizationRequest request) {
        AccessContext.admin();
        OrganizationEntity existing = required(id, true);
        if ("DELETED".equals(existing.getStatus())) throw new IllegalArgumentException("已删除机构不能编辑。");
        if (!existing.getOrgCode().equals(request.getOrgCode())) throw new IllegalArgumentException("机构编码不可更改，请保留历史标识。");
        OrganizationEntity row = values(request);
        row.setId(id);
        organizations.update(row);
        audit.record("ORGANIZATION_UPDATE", "ORGANIZATION", id, row.getOrgCode());
        return organizations.get(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrganizationEntity status(Long id, String status) {
        AccessContext.admin();
        if (!Set.of("ACTIVE", "DISABLED", "DELETED").contains(status)) throw new IllegalArgumentException("机构状态无效。");
        OrganizationEntity row = required(id, true);
        if ("DELETED".equals(row.getStatus())) {
            if ("DELETED".equals(status)) return row;
            throw new IllegalArgumentException("已删除机构不能重新启用，原编码保留。");
        }
        if (!"ACTIVE".equals(status) && id.equals(AccessContext.require().getOrganizationId())) {
            throw new IllegalArgumentException("不能停用或删除当前登录账号所属机构，避免失去管理入口。");
        }
        if ("DELETED".equals(status) && !empty(organizations.dependencies(id))) {
            throw new IllegalArgumentException("机构仍关联人员或业务记录，不能删除；请核对依赖或使用停用。");
        }
        if (status.equals(row.getStatus())) return row;
        organizations.status(id, status);
        audit.record("ORGANIZATION_" + status, "ORGANIZATION", id, row.getOrgCode());
        return organizations.get(id);
    }

    private OrganizationEntity required(Long id, boolean lock) {
        OrganizationEntity row = lock ? organizations.lock(id) : organizations.get(id);
        if (row == null) throw new IllegalArgumentException("机构不存在。");
        return row;
    }

    private boolean empty(List<Map<String, Object>> dependencies) {
        return dependencies.stream().allMatch(row -> ((Number) row.get("total")).longValue() == 0);
    }

    private OrganizationEntity values(OrganizationRequest request) {
        OrganizationEntity row = new OrganizationEntity();
        row.setOrgCode(request.getOrgCode());
        row.setOrgName(request.getOrgName().trim());
        row.setOrgType(request.getOrgType());
        row.setContactName(text(request.getContactName()));
        row.setContactPhone(text(request.getContactPhone()));
        row.setAddress(text(request.getAddress()));
        return row;
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
