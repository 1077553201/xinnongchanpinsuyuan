package org.nongchanpin.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.nongchanpin.model.command.AppendTraceEventCommand;
import org.nongchanpin.model.command.BindItemCommand;
import org.nongchanpin.model.command.RegisterBatchCommand;

class ChainParameterBuilderTest {
    @Test
    void registerBatchBuildsBytes32AndUint256Parameters() {
        RegisterBatchCommand command = new RegisterBatchCommand();
        command.setBatchCode("BATCH-001");
        command.setProductCode("RICE-001");
        command.setPayload(Collections.singletonMap("origin", "xinhui"));
        command.setStartedAt(1783450000L);

        ChainParameterBuilder.RegisterBatchParams params = ChainParameterBuilder.registerBatch(command);

        assertEquals(32, params.getBatchIdHash().length);
        assertEquals(32, params.getProductIdHash().length);
        assertEquals(32, params.getPayloadHash().length);
        assertEquals(BigInteger.valueOf(1783450000L), params.getStartedAt());
    }

    @Test
    void bindItemBuildsHashedItemAndBatchParameters() {
        BindItemCommand command = new BindItemCommand();
        command.setItemCode("TRACE-001");
        command.setBatchCode("BATCH-001");
        command.setPayload(Collections.singletonMap("level", "A"));

        ChainParameterBuilder.BindItemParams params = ChainParameterBuilder.bindItem(command);

        assertEquals(32, params.getItemCodeHash().length);
        assertEquals(32, params.getBatchIdHash().length);
        assertEquals(32, params.getPayloadHash().length);
    }

    @Test
    void appendTraceEventBuildsEventTypeAndOccurredAt() {
        AppendTraceEventCommand command = new AppendTraceEventCommand();
        command.setItemCode("TRACE-001");
        command.setEventId("EVENT-001");
        command.setEventType(5);
        command.setPayload(Collections.singletonMap("result", "PASS"));
        command.setOccurredAt(1783450099L);

        ChainParameterBuilder.AppendTraceEventParams params = ChainParameterBuilder.appendTraceEvent(command);

        assertEquals(32, params.getItemCodeHash().length);
        assertEquals(32, params.getEventIdHash().length);
        assertEquals(BigInteger.valueOf(5), params.getEventType());
        assertEquals(BigInteger.valueOf(1783450099L), params.getOccurredAt());
    }
}
