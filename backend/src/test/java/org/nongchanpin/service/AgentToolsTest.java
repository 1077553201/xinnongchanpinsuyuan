package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import javax.validation.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.AgentModels.Operation;
import org.nongchanpin.security.*;

class AgentToolsTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    private final GrowingAccess growing=mock(GrowingAccess.class);
    private final HarvestService harvests=mock(HarvestService.class);
    private final TransportService transport=mock(TransportService.class);
    private final ValidatorFactory factory=Validation.buildDefaultValidatorFactory();
    private final AgentTools tools=new AgentTools(json,factory.getValidator(),growing,mock(ProductionMapper.class),mock(TransportMapper.class),harvests,transport,new PayloadService());
    private final PlantingCycleEntity cycle=new PlantingCycleEntity();
    @BeforeEach void setup(){Actor a=new Actor();a.setId(1L);a.setOrganizationId(2L);a.setRole("MANAGER");AccessContext.set(a);cycle.setId(3L);cycle.setPlotId(4L);cycle.setProductId(5L);cycle.setStatus("ACTIVE");cycle.setPlantedOn(LocalDate.now().minusDays(2));cycle.setVariety("品种");when(growing.cycle(eq(3L),anyBoolean())).thenReturn(cycle);}
    @AfterEach void cleanup(){AccessContext.clear();factory.close();}
    private Operation op() throws Exception {Operation op=new Operation();op.setId(UUID.randomUUID().toString());op.setKind("HARVEST");op.setQuote("采收10斤");op.setTargetId(3L);op.setFields((com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("{\"lotCode\":\"QA-AGENT\",\"quantity\":10,\"unit\":\"jin\",\"harvestedAt\":\"2026-10-22 12:00:00\",\"performerName\":\"现场人\"}"));return op;}
    @Test void preparingDraftDoesNotCallBusinessWrite() throws Exception {Operation op=tools.prepare(op());assertTrue(op.getIssues().isEmpty());assertNotNull(op.getContextHash());verifyNoInteractions(harvests,transport);}
    @Test void missingFieldsAndUnknownSourceRemainQuestions() throws Exception {Operation op=op();op.getFields().remove("harvestedAt");op.setTargetId(null);tools.prepare(op);assertTrue(op.getIssues().stream().anyMatch(x->x.contains("采收时间")));assertNull(op.getContextHash());}
    @Test void unsupportedFieldsCannotSmuggleSqlOrConfirmationKey() throws Exception {Operation op=op();op.getFields().put("requestKey","forged");assertThrows(IllegalArgumentException.class,()->tools.prepare(op));}
    @Test void notesCannotCallToolsEvenWithMaliciousPayload() throws Exception {Operation op=op();op.setKind("NOTE");tools.prepare(op);assertEquals(0,op.getFields().size());assertFalse(op.getIssues().isEmpty());verifyNoInteractions(harvests,transport);}
    @Test void stateChangeBlocksBeforeBusinessWrite() throws Exception {Operation op=tools.prepare(op());cycle.setStatus("ENDED");assertThrows(IllegalArgumentException.class,()->tools.execute(1L,op));verifyNoInteractions(harvests,transport);}
    @Test void successfulExecutionUsesStableServerGeneratedRequestKey() throws Exception {Operation op=tools.prepare(op());HarvestLotEntity lot=new HarvestLotEntity();lot.setId(8L);when(harvests.create(eq(3L),any())).thenReturn(lot);assertEquals(8L,tools.execute(7L,op));verify(harvests).create(eq(3L),argThat(r->r.getRequestKey().equals(tools.key(7L,op.getId()))));assertEquals(tools.key(7L,op.getId()),tools.key(7L,op.getId()));assertNotEquals(tools.key(7L,op.getId()),tools.key(8L,op.getId()));}
}
