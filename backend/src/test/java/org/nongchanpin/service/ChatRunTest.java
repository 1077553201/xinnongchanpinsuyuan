package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.nongchanpin.mapper.ChatMapper;
import org.nongchanpin.model.entity.ChatModels.Turn;

class ChatRunTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test void fallbackKeepsActualStockQuantitiesAndUnitsInsteadOfOnlyGenericFailure() {
        ChatRun run = new ChatRun(mock(ChatMapper.class), json, new Turn());
        run.events.add(Map.of("kind", "READ", "label", "仓库现货", "note", "返回 1 条", "data", Map.of("rows", List.of(Map.of("id", 2, "productName", "草莓", "availableCount", 4, "packageUnit", "盒", "availableWeightKg", "1.2")))));
        String reply = run.fallback();
        assertTrue(reply.contains("草莓"));
        assertTrue(reply.contains("在库 4盒"));
        assertTrue(reply.contains("净重 1.2 kg"));
        assertTrue(reply.contains("没有执行业务"));
    }

    @Test void normalizedQueriesExcludeInjectedScopeAndUnsupportedFilters() throws Exception {
        JsonNode request = ChatRun.query(json, json.readTree("{\"tool\":\"plots\",\"status\":\"ACTIVE\",\"search\":\" 东棚 \",\"organizationId\":99}"));
        assertFalse(request.has("organizationId"));
        assertEquals("", request.path("status").asText());
        assertEquals("东棚", request.path("search").asText());
        assertEquals("PLOTS", request.path("tool").asText());
    }
}
