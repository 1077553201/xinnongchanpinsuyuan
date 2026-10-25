package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.QualityEvidenceRequests.Replace;
import org.nongchanpin.model.entity.SourceQualityModels.Report;
import org.nongchanpin.model.entity.QualityEvidenceModels.Revision;
import org.nongchanpin.security.*;

class QualityRevisionServiceTest {
    private final SourceQualityService quality=mock(SourceQualityService.class);
    private final SourceQualityMapper reports=mock(SourceQualityMapper.class);
    private final QualityRevisionMapper revisions=mock(QualityRevisionMapper.class);
    private final QualityRevisionService service=new QualityRevisionService(quality,reports,revisions,mock(AuditService.class));
    private final Map<Long,Report> rows=new HashMap<>();private final Map<Long,Revision> links=new HashMap<>();private final Actor actor=new Actor();
    private final Report old=report(1L,"WITHDRAWN"),current=report(3L,"DRAFT");
    private Report report(Long id,String status){Report r=new Report();r.setId(id);r.setOrganizationId(3L);r.setHarvestId(17L);r.setPackingId(223L);r.setScopeType("PACKING");r.setStatus(status);r.setManifestHash(String.valueOf(id).repeat(64));return r;}
    @BeforeEach void setup(){actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);rows.put(1L,old);rows.put(3L,current);when(quality.checkedReport(anyLong())).thenAnswer(c->{Report r=rows.get(c.getArgument(0));if(r==null)throw new IllegalArgumentException();AccessContext.checkOrganization(r.getOrganizationId());return r;});when(reports.lock(anyLong())).thenAnswer(c->rows.get(c.getArgument(0)));when(revisions.previous(any())).thenAnswer(c->links.values().stream().filter(r->r.getReplacementReportId().equals(c.getArgument(0))).findFirst().orElse(null));when(revisions.next(any())).thenAnswer(c->links.get(c.getArgument(0)));doAnswer(c->{Revision r=c.getArgument(0);links.put(r.getPreviousReportId(),r);return 1;}).when(revisions).insert(any());}
    @AfterEach void clear(){AccessContext.clear();}
    private Replace request(){Replace r=new Replace();r.setPreviousReportId(1L);r.setExpectedPreviousHash(old.getManifestHash());r.setExpectedReplacementHash(current.getManifestHash());r.setReason("PRIVATE replacement reason");return r;}
    @Test void detailIsReadOnly(){service.detail(3L,1L);verify(revisions,never()).insert(any());}
    @Test void linksWithoutPublishingOrOverwritingOriginal(){Revision r=service.link(3L,request());assertEquals(1L,r.getRecordedBy());assertEquals("WITHDRAWN",old.getStatus());assertEquals("DRAFT",current.getStatus());verify(reports,never()).publish(any());verify(reports,never()).withdraw(any());}
    @Test void identicalReplayRetainsOriginalEvenAfterNewReportPublished(){service.link(3L,request());current.setStatus("PUBLISHED");actor.setId(2L);Revision again=service.link(3L,request());assertEquals(1L,again.getRecordedBy());verify(revisions,times(1)).insert(any());}
    @Test void cannotChangeReasonOrHashesOnReplay(){service.link(3L,request());Replace changed=request();changed.setReason("different");assertThrows(IllegalArgumentException.class,()->service.link(3L,changed));Replace wrong=request();wrong.setExpectedPreviousHash("0".repeat(64));assertThrows(IllegalArgumentException.class,()->service.link(3L,wrong));}
    @Test void sourceScopeMustMatchExactly(){current.setPackingId(224L);assertThrows(IllegalArgumentException.class,()->service.link(3L,request()));current.setPackingId(223L);current.setScopeType("HARVEST");assertThrows(IllegalArgumentException.class,()->service.link(3L,request()));}
    @Test void chronologyPreventsSelfReferenceAndCycles(){Replace r=request();r.setPreviousReportId(3L);assertThrows(IllegalArgumentException.class,()->service.link(3L,r));assertThrows(IllegalArgumentException.class,()->service.detail(1L,3L));}
    @Test void cannotBranchFromOneOriginal(){service.link(3L,request());Report other=report(4L,"DRAFT");rows.put(4L,other);Replace r=request();r.setExpectedReplacementHash(other.getManifestHash());assertThrows(IllegalArgumentException.class,()->service.link(4L,r));}
    @Test void oldMustBeWithdrawnAndReplacementUnpublished(){old.setStatus("PUBLISHED");assertThrows(IllegalArgumentException.class,()->service.link(3L,request()));old.setStatus("WITHDRAWN");current.setStatus("PUBLISHED");assertThrows(IllegalArgumentException.class,()->service.link(3L,request()));}
    @Test void usesHarvestThenAscendingReportLocks(){service.link(3L,request());org.mockito.InOrder order=inOrder(reports);order.verify(reports).lockHarvest(17L);order.verify(reports).lock(1L);order.verify(reports).lock(3L);}
    @Test void rolesAndOrganizationsCannotBypassRevisionRules(){actor.setRole("VIEWER");assertThrows(AccessException.class,()->service.link(3L,request()));actor.setRole("MANAGER");actor.setOrganizationId(8L);assertThrows(AccessException.class,()->service.detail(3L,null));assertThrows(AccessException.class,()->service.link(3L,request()));}
}
