package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.InventoryMapper;
import org.nongchanpin.mapper.ProductionMapper;
import org.nongchanpin.model.entity.WarehouseEntity;
import org.nongchanpin.security.*;

class WarehouseStockServiceTest {
    private final InventoryMapper mapper=mock(InventoryMapper.class);
    private final InventoryService service=new InventoryService(mapper,mock(ProductionMapper.class),mock(GrowingAccess.class),new PayloadService(),mock(AuditService.class));
    private final Actor actor=new Actor();
    @BeforeEach void setup() {
        actor.setId(1L); actor.setRole("VIEWER"); actor.setOrganizationId(3L); AccessContext.set(actor);
        WarehouseEntity warehouse=new WarehouseEntity(); warehouse.setId(11L); warehouse.setOrganizationId(3L); when(mapper.warehouse(11L)).thenReturn(warehouse);
    }
    @AfterEach void clear() { AccessContext.clear(); }
    @Test void readonlyCanQueryWholeWarehouseWithoutCycle() {
        service.warehouseStock(11L,"",false,1,20); verify(mapper).warehouseStock(11L,3L,"",false,0L,20); verify(mapper).warehouseSummary(11L,3L);
    }
    @Test void otherOrganizationCannotQueryWarehouse() {
        actor.setOrganizationId(9L); assertThrows(AccessException.class,()->service.warehouseStock(11L,"",false,1,20)); verify(mapper,never()).warehouseStockCount(any(),any(),anyString(),anyBoolean());
    }
    @Test void administratorCanQueryWarehouseOfOtherOrganization() {
        actor.setOrganizationId(9L); actor.setRole("ADMIN"); service.warehouseStock(11L,"",false,1,20); verify(mapper).warehouseStockCount(11L,3L,"",false);
    }
    @Test void paginationHasNoTwoHundredRowLimitAndClampsToLastPage() {
        when(mapper.warehouseStockCount(11L,3L,"",false)).thenReturn(205L);
        Map<String,Object> result=service.warehouseStock(11L,"",false,999,100); assertEquals(3L,result.get("page")); assertEquals(205L,result.get("total")); verify(mapper).warehouseStock(11L,3L,"",false,200L,100);
    }
    @Test void filtersDoNotChangeWarehouseSummaryScope() {
        service.warehouseStock(11L,"  草莓  ",true,1,50); verify(mapper).warehouseStockCount(11L,3L,"草莓",true); verify(mapper).warehouseUnits(11L,3L); verify(mapper).warehouseSummary(11L,3L);
    }
    @Test void rejectsMissingWarehouse() { assertThrows(IllegalArgumentException.class,()->service.warehouseStock(99L,"",false,1,20)); }
    @Test void rejectsInvalidPaginationAndOversizedSearch() {
        assertThrows(IllegalArgumentException.class,()->service.warehouseStock(11L,"",false,0,20));
        assertThrows(IllegalArgumentException.class,()->service.warehouseStock(11L,"",false,1,101));
        assertThrows(IllegalArgumentException.class,()->service.warehouseStock(11L,"",false,1,0));
        assertThrows(IllegalArgumentException.class,()->service.warehouseStock(11L,"x".repeat(129),false,1,20));
    }
    @Test void emptyWarehouseStillHasFirstPage() { assertEquals(1L,service.warehouseStock(11L,"",false,100,20).get("page")); }
}
