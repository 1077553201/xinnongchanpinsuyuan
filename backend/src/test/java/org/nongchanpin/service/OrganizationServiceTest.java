package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.OrganizationMapper;
import org.nongchanpin.model.dto.OrganizationRequest;
import org.nongchanpin.model.entity.OrganizationEntity;
import org.nongchanpin.security.*;

class OrganizationServiceTest {
    private final OrganizationMapper mapper = mock(OrganizationMapper.class);
    private final AuditService audit = mock(AuditService.class);
    private final OrganizationService service = new OrganizationService(mapper, audit);
    private OrganizationEntity row;

    @BeforeEach void setup() {
        Actor actor = new Actor(); actor.setId(1L); actor.setOrganizationId(1L); actor.setRole("ADMIN"); AccessContext.set(actor);
        row = new OrganizationEntity(); row.setId(2L); row.setOrgCode("FARM-2"); row.setOrgName("二号农场"); row.setStatus("ACTIVE");
        when(mapper.get(2L)).thenReturn(row); when(mapper.lock(2L)).thenReturn(row);
        when(mapper.dependencies(2L)).thenReturn(List.of(Map.of("label", "人员账号", "total", 0L)));
    }
    @AfterEach void cleanup() { AccessContext.clear(); }

    @Test void onlyAdministratorsCanManageOrReadOrganizationDetails() {
        for (String role : List.of("MANAGER", "VIEWER")) {
            AccessContext.require().setRole(role);
            assertThrows(AccessException.class, () -> service.list("", "ALL", 1));
            assertThrows(AccessException.class, () -> service.detail(2L));
            assertThrows(AccessException.class, () -> service.create(request()));
            assertThrows(AccessException.class, () -> service.update(2L, request()));
            assertThrows(AccessException.class, () -> service.status(2L, "DISABLED"));
        }
        verifyNoInteractions(mapper, audit);
    }
    @Test void createTrimsContactAndRecordsAuditWithoutChangingOtherOrganizations() {
        doAnswer(call -> { OrganizationEntity created = call.getArgument(0); created.setId(3L); assertEquals("机构名", created.getOrgName()); assertEquals("联系人", created.getContactName()); return 1; }).when(mapper).insert(any());
        service.create(request());
        verify(audit).record(eq("ORGANIZATION_CREATE"), eq("ORGANIZATION"), eq(3L), eq("FARM-2"));
        verify(mapper, never()).status(any(), any());
    }
    @Test void codeCannotBeRewrittenDuringEdit() {
        OrganizationRequest request = request(); request.setOrgCode("DIFFERENT");
        assertThrows(IllegalArgumentException.class, () -> service.update(2L, request));
        verify(mapper, never()).update(any());
    }
    @Test void editPreservesIdAndStatus() {
        service.update(2L, request());
        verify(mapper).update(argThat(value -> value.getId().equals(2L) && value.getOrgCode().equals("FARM-2")));
        verify(mapper, never()).status(any(), any());
    }
    @Test void currentAccountOrganizationCannotBeDisabledOrDeleted() {
        AccessContext.require().setOrganizationId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.status(2L, "DISABLED"));
        assertThrows(IllegalArgumentException.class, () -> service.status(2L, "DELETED"));
        assertEquals(false, service.detail(2L).get("canDelete"));
        verify(mapper, never()).status(any(), any());
    }
    @Test void evenOneArchivedReferencePreventsDeletionAfterPreview() {
        assertEquals(true, service.detail(2L).get("canDelete"));
        when(mapper.dependencies(2L)).thenReturn(List.of(Map.of("label", "产品", "total", 1L)));
        assertThrows(IllegalArgumentException.class, () -> service.status(2L, "DELETED"));
        verify(mapper, never()).status(any(), any());
    }
    @Test void unusedOrganizationCanBeLogicallyDeletedAndItsCodeIsKept() {
        service.status(2L, "DELETED");
        verify(mapper).status(2L, "DELETED");
        verify(audit).record("ORGANIZATION_DELETED", "ORGANIZATION", 2L, "FARM-2");
    }
    @Test void inactiveOrganizationRetainsDependenciesAndCanBeEnabledAgain() {
        when(mapper.dependencies(2L)).thenReturn(List.of(Map.of("label", "人员账号", "total", 3L)));
        service.status(2L, "DISABLED"); verify(mapper).status(2L, "DISABLED");
        row.setStatus("DISABLED"); service.status(2L, "ACTIVE"); verify(mapper).status(2L, "ACTIVE");
    }
    @Test void deletedOrganizationsCannotBeEditedOrReenabled() {
        row.setStatus("DELETED");
        assertThrows(IllegalArgumentException.class, () -> service.update(2L, request()));
        assertThrows(IllegalArgumentException.class, () -> service.status(2L, "ACTIVE"));
        assertEquals(row, service.status(2L, "DELETED"));
        verify(mapper, never()).status(any(), any());
    }
    @Test void repeatedStatusDoesNotWriteAnotherAudit() {
        service.status(2L, "ACTIVE"); verifyNoInteractions(audit); verify(mapper, never()).status(any(), any());
    }
    @Test void listValidatesFiltersAndClampsPage() {
        assertThrows(IllegalArgumentException.class, () -> service.list("", "INVALID", 1));
        assertThrows(IllegalArgumentException.class, () -> service.list("", "ACTIVE", 0));
        when(mapper.count("农场", "ALL")).thenReturn(1L);
        service.list(" 农场 ", "ALL", 200);
        verify(mapper).list("农场", "ALL", 20, 0);
    }
    private OrganizationRequest request() {
        OrganizationRequest request = new OrganizationRequest(); request.setOrgCode("FARM-2"); request.setOrgName(" 机构名 "); request.setOrgType("FARM"); request.setContactName(" 联系人 "); return request;
    }
}
