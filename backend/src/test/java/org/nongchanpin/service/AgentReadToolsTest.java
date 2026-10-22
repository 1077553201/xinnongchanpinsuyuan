package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.ChatMapper;
import org.nongchanpin.security.*;

class AgentReadToolsTest {
    private final ChatMapper mapper=mock(ChatMapper.class);
    private final InventoryService inventory=mock(InventoryService.class);
    private final ObjectMapper json=new ObjectMapper();
    private final AgentReadTools tools=new AgentReadTools(mapper,inventory,mock(SalesService.class),mock(TransportService.class),mock(HarvestService.class),json);
    @BeforeEach void setup(){Actor actor=new Actor();actor.setId(1L);actor.setRole("MANAGER");actor.setOrganizationId(3L);AccessContext.set(actor);}
    @AfterEach void cleanup(){AccessContext.clear();}
    @Test void queriesAlwaysUseCurrentActorOrganization()throws Exception{when(mapper.plots(3L,"东棚")).thenReturn(List.of());tools.read(json.readTree("{\"tool\":\"PLOTS\",\"search\":\"东棚\",\"organizationId\":99}"));verify(mapper).plots(3L,"东棚");}
    @Test void arbitrarySqlAndUnknownToolsAreRejected()throws Exception{assertThrows(IllegalArgumentException.class,()->tools.read(json.readTree("{\"tool\":\"DELETE_SQL\"}")));verifyNoInteractions(mapper,inventory);}
    @Test void explicitCycleIdUsesExactScopedLookupInsteadOfSearchingIdAsText()throws Exception{when(mapper.cycleById(3L,25L)).thenReturn(List.of());tools.read(json.readTree("{\"tool\":\"CYCLES\",\"targetId\":25,\"search\":\"ID=25\"}"));verify(mapper).cycleById(3L,25L);verify(mapper,never()).cycles(any(),any());}
    @Test void stockNeedsExplicitWarehouseAndRetainsBusinessAccessCheck()throws Exception{assertThrows(IllegalArgumentException.class,()->tools.read(json.readTree("{\"tool\":\"STOCK\"}")));when(inventory.warehouseStock(9L,"",false,1,10)).thenThrow(new AccessException(403,"其他机构"));assertThrows(AccessException.class,()->tools.read(json.readTree("{\"tool\":\"STOCK\",\"targetId\":9}")));}
}
