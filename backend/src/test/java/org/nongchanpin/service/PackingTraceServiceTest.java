package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.IssuePackingCodesRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.test.util.ReflectionTestUtils;

class PackingTraceServiceTest {
    private final PackingTraceMapper mapper=mock(PackingTraceMapper.class);
    private final ProductBatchMapper batches=mock(ProductBatchMapper.class);
    private final TraceItemMapper items=mock(TraceItemMapper.class);
    private final PackingTraceService service=new PackingTraceService(mapper,batches,items,new PayloadService(),mock(AuditService.class));
    private final Source source=new Source();
    private final Actor actor=new Actor();
    @BeforeEach void setup() {
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        source.setId(9L);source.setOrganizationId(3L);source.setPackageCount(4);source.setProductId(2L);source.setPackageUnit("盒");source.setNetWeightKg(new BigDecimal("0.500"));source.setPackingCode("QA-PACK");source.setPlotName("东棚");source.setCycleCode("CYCLE-OLD");source.setProductName("草莓");source.setVariety("红颜");source.setPlantedOn(LocalDate.now().minusDays(30));source.setHarvestedAt(LocalDateTime.now().minusDays(1));
        when(mapper.source(9L)).thenReturn(source);
        ReflectionTestUtils.setField(service,"baseUrl","http://localhost:5173/t");
        doAnswer(c->{ProductBatchEntity b=c.getArgument(0);b.setId(20L);when(batches.findById(20L)).thenReturn(b);return 1;}).when(batches).insert(any());
        doAnswer(c->{Issue i=c.getArgument(0);i.setId(30L);return 1;}).when(mapper).insertIssue(any());
        doAnswer(c->{TraceItemEntity i=c.getArgument(0);i.setId(40L);return 1;}).when(items).insert(any());
        when(mapper.issueItems(any())).thenReturn(List.of());
    }
    @AfterEach void clear(){AccessContext.clear();}
    private IssuePackingCodesRequest request(int count){IssuePackingCodesRequest r=new IssuePackingCodesRequest();r.setCount(count);r.setRequestKey(UUID.randomUUID().toString());return r;}
    @Test void generationCreatesOneItemPerPackageAndDoesNotClaimChainConfirmation() {
        service.generate(9L,request(2));
        verify(items,times(2)).insert(argThat(i->"NOT_SUBMITTED".equals(i.getChainStatus())&&i.getTraceUrl().contains(i.getTraceCodePlain())));
        verify(mapper).link(40L,9L,30L,1L);verify(mapper).link(40L,9L,30L,2L);
        verify(mapper).insertBridge(argThat(b->b.getSourceJson().contains("CYCLE-OLD")&&b.getSourceJson().contains("0.500")));
    }
    @Test void quantityCannotExceedRemainingEvenWhenPreviousAssignmentsAreNotOnCurrentPage() {
        when(mapper.count(9L)).thenReturn(3L);
        assertThrows(IllegalArgumentException.class,()->service.generate(9L,request(2)));
        verifyNoInteractions(items,batches);verify(mapper,never()).insertIssue(any());
    }
    @Test void sameRequestReturnsOriginalIssueWithoutAssigningAgain() {
        IssuePackingCodesRequest r=request(2);service.generate(9L,r);
        org.mockito.ArgumentCaptor<Issue> captured=org.mockito.ArgumentCaptor.forClass(Issue.class);verify(mapper).insertIssue(captured.capture());
        when(mapper.byKey(r.getRequestKey())).thenReturn(captured.getValue());
        service.generate(9L,r);verify(items,times(2)).insert(any());
        r.setCount(1);assertThrows(IllegalArgumentException.class,()->service.generate(9L,r));
    }
    @Test void viewerCannotGenerateButCanReadScopedDetail() {
        actor.setRole("VIEWER");when(mapper.items(9L,0L)).thenReturn(List.of());
        assertNotNull(service.detail(9L,1));assertThrows(AccessException.class,()->service.generate(9L,request(1)));verifyNoInteractions(items,batches);
    }
    @Test void crossOrganizationCannotReadOrAssign() {
        actor.setOrganizationId(99L);assertThrows(AccessException.class,()->service.detail(9L,1));assertThrows(AccessException.class,()->service.generate(9L,request(1)));verify(mapper,never()).lock(any());
    }
    @Test void existingSnapshotIsReusedRatherThanChangedByLaterPlotNames() {
        Bridge b=new Bridge();b.setBatchId(20L);b.setPackingId(9L);b.setSourceJson("{\"plotName\":\"原地块\",\"privateKey\":\"must-not-expose\"}");b.setSourceHash(HashUtils.hashCanonicalTextPlainHex(b.getSourceJson()));
        when(mapper.bridge(9L)).thenReturn(b);ProductBatchEntity batch=new ProductBatchEntity();batch.setId(20L);batch.setStatus("ACTIVE");when(batches.findById(20L)).thenReturn(batch);
        source.setPlotName("新地块名称");service.generate(9L,request(1));verify(mapper,never()).insertBridge(any());
        assertEquals("原地块",PackingTraceService.publicSource(b).get("plotName"));assertFalse(PackingTraceService.publicSource(b).containsKey("privateKey"));
    }
    @Test void damagedSnapshotIsNotPresentedAsValidSource() {
        Bridge b=new Bridge();b.setSourceJson("{}");b.setSourceHash("wrong");assertThrows(IllegalArgumentException.class,()->PackingTraceService.publicSource(b));
    }
    @Test void invalidCountCannotCreateItems() {
        assertThrows(IllegalArgumentException.class,()->service.generate(9L,request(0)));assertThrows(IllegalArgumentException.class,()->service.generate(9L,request(101)));verifyNoInteractions(items,batches);
    }
}
