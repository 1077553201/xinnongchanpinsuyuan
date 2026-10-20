package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.vo.ChainBatchView;
import org.nongchanpin.model.vo.ChainItemView;
import org.nongchanpin.model.vo.ChainTraceEventView;
import org.nongchanpin.model.vo.PublicTraceView;

class PublicChainVerifierTest {
    private PublicTraceView.ChainVerification result() {
        PublicTraceView.ChainVerification value = new PublicTraceView.ChainVerification();
        List<PublicTraceView.Record> records = new ArrayList<>();
        for (String type : Arrays.asList("BATCH_REGISTER", "ITEM_BIND")) {
            PublicTraceView.Record record = new PublicTraceView.Record();
            record.setBusinessType(type);
            record.setPayloadHashMatched(true); // 已保存的数据库结果不能代替实时读取。
            records.add(record);
        }
        value.setRecords(records);
        return value;
    }

    @Test
    void comparesFreshChainHashesAndDetectsMissingEvents() {
        ChainService chain = mock(ChainService.class);
        ChainBatchView batch = new ChainBatchView(); batch.setExists(true); batch.setPayloadHash("0xaaa");
        ChainItemView item = new ChainItemView(); item.setExists(true); item.setPayloadHash("0xbbb"); item.setTraceEventCount(BigInteger.ZERO);
        when(chain.getBatch("B1")).thenReturn(batch);
        when(chain.getItem("T1")).thenReturn(item);
        ProductBatchEntity entity = new ProductBatchEntity(); entity.setBatchCode("B1");
        Map<String,String> expected = Map.of("BATCH_REGISTER", "0xaaa", "ITEM_BIND", "0xbbb");
        PublicTraceView.ChainVerification result = result();
        new PublicChainVerifier(chain).verify("T1", entity, Collections.emptyList(), result, expected);
        assertEquals("LIVE_CHAIN", result.getVerificationSource());
        assertTrue(result.isBatchPayloadHashMatched()); assertTrue(result.isItemPayloadHashMatched()); assertTrue(result.isEventsComplete());
        batch.setPayloadHash("0xchanged");
        new PublicChainVerifier(chain).verify("T1", entity, Collections.emptyList(), result, expected);
        assertFalse(result.isBatchPayloadHashMatched());
    }

    @Test
    void nodeFailureCannotReuseSavedSuccessAsLiveEvidence() {
        ChainService chain = mock(ChainService.class);
        when(chain.getBatch("B1")).thenThrow(new IllegalStateException("offline"));
        ProductBatchEntity batch = new ProductBatchEntity(); batch.setBatchCode("B1");
        PublicTraceView.ChainVerification result = result();
        new PublicChainVerifier(chain).verify("T1", batch, Collections.emptyList(), result, Collections.emptyMap());
        assertEquals("UNAVAILABLE", result.getVerificationSource());
        assertTrue(result.getRecords().stream().allMatch(record -> "UNAVAILABLE".equals(record.getVerificationSource())));
    }

    @Test
    void detectsChainEventsOmittedFromBusinessTimeline() {
        ChainService chain = mock(ChainService.class);
        ChainBatchView batchView = new ChainBatchView(); batchView.setExists(true); batchView.setPayloadHash("0xaaa");
        ChainItemView item = new ChainItemView(); item.setExists(true); item.setPayloadHash("0xbbb"); item.setTraceEventCount(BigInteger.ONE);
        ChainTraceEventView event = new ChainTraceEventView(); event.setEventIdHash("0xevent"); event.setPayloadHash("0xeventPayload");
        when(chain.getBatch("B1")).thenReturn(batchView);
        when(chain.getItem("T1")).thenReturn(item);
        when(chain.getTraceEvent("T1", 0)).thenReturn(event);
        ProductBatchEntity batch = new ProductBatchEntity(); batch.setBatchCode("B1");
        PublicTraceView.ChainVerification result = result();
        new PublicChainVerifier(chain).verify("T1", batch, Collections.emptyList(), result, Map.of("BATCH_REGISTER", "0xaaa", "ITEM_BIND", "0xbbb"));
        assertEquals("LIVE_CHAIN", result.getVerificationSource());
        assertFalse(result.isEventsComplete());
    }
}
