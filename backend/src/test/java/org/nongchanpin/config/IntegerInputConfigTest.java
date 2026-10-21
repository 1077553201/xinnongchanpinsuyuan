package org.nongchanpin.config;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.nongchanpin.model.dto.InventoryRequests;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class IntegerInputConfigTest {
    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder=new Jackson2ObjectMapperBuilder();
        new IntegerInputConfig().strictIntegerInputs().customize(builder);
        return builder.build();
    }
    @Test void fractionalPackageCountCannotBeSilentlyTruncated() {
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->mapper().readValue("{\"packageCount\":1.5}",InventoryRequests.Movement.class));
    }
    @Test void wholePackageCountIsAccepted() throws Exception {
        assertEquals(2,mapper().readValue("{\"packageCount\":2}",InventoryRequests.Movement.class).getPackageCount());
    }
}
