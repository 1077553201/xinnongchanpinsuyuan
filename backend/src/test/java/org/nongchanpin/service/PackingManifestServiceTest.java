package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.nongchanpin.mapper.PackingEvidenceMapper;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.util.HashUtils;

class PackingManifestServiceTest {
    private final PackingEvidenceMapper mapper=mock(PackingEvidenceMapper.class);
    private Bridge bridge(){Bridge b=new Bridge();b.setSourceJson("{\"plantedOn\":\"2026-10-01\",\"harvestedAt\":\"2026-10-10T12:00:00\"}");b.setSourceHash(HashUtils.hashCanonicalTextPlainHex(b.getSourceJson()));return b;}
    @Test void timeRangeComesFromOriginalSourceRatherThanNextSeasonOrCurrentTime() {
        Source source=new Source();source.setCycleId(4L);source.setPlantedOn(LocalDate.of(2026,10,20));
        Map<String,Object> result=new PackingManifestService(mapper).build(source,bridge());
        assertEquals(LocalDateTime.of(2026,10,10,12,0),result.get("until"));
        verify(mapper).environment(4L,LocalDateTime.of(2026,10,1,0,0),LocalDateTime.of(2026,10,10,12,0));
        assertEquals("NOT_LINKED_TO_ITEM",result.get("circulationStatus"));
    }
    @Test void capIsExplicitAndDatabaseTimesAreStableLocalStrings() {
        Source source=new Source();source.setCycleId(4L);
        List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<51;i++)rows.add(Map.of("metric","TEMPERATURE","window_start",java.sql.Timestamp.valueOf("2026-10-02 10:00:00")));
        when(mapper.environment(any(),any(),any())).thenReturn(rows);
        Map<String,Object> result=new PackingManifestService(mapper).build(source,bridge());Map<?,?> section=(Map<?,?>)result.get("environment");
        assertEquals(true,section.get("truncated"));assertEquals(50,((List<?>)section.get("rows")).size());assertTrue(section.toString().contains("2026-10-02T10:00"));
    }
}
