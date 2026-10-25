package org.nongchanpin.service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.TraceEventEntity;
import org.nongchanpin.model.vo.ChainBatchView;
import org.nongchanpin.model.vo.ChainItemView;
import org.nongchanpin.model.vo.ChainTraceEventView;
import org.nongchanpin.model.vo.PublicTraceView;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicChainVerifier {
    private static final BigInteger MAX_EVENTS = BigInteger.valueOf(200);
    private final ChainService chain;

    public void verify(String traceCode, ProductBatchEntity batch, List<TraceEventEntity> events,
            PublicTraceView.ChainVerification verification, Map<String, String> expected) {
        verification.setVerificationSource("UNAVAILABLE");
        for (PublicTraceView.Record record : verification.getRecords()) record.setVerificationSource("UNAVAILABLE");
        if (batch == null) return;
        try {
            ChainBatchView chainBatch = chain.getBatch(batch.getBatchCode());
            ChainItemView chainItem = chain.getItem(traceCode);
            if (chainBatch == null || chainItem == null) return;
            if (chainBatch.getStatus() != null) verification.setChainBatchStatus(chainBatch.getStatus().intValue());
            if (chainItem.getStatus() != null) verification.setChainItemStatus(chainItem.getStatus().intValue());
            Map<String, String> hashes = new HashMap<>();
            boolean productMatches=!expected.containsKey("BATCH_PRODUCT_ID")||expected.get("BATCH_PRODUCT_ID").equalsIgnoreCase(chainBatch.getProductIdHash());
            boolean linked=chainBatch.isExists()&&chainItem.isExists()&&HashUtils.hashBusinessIdHex(batch.getBatchCode()).equalsIgnoreCase(chainItem.getBatchIdHash());
            hashes.put("BATCH_REGISTER", chainBatch.isExists()&&productMatches ? chainBatch.getPayloadHash() : null);
            hashes.put("ITEM_BIND", linked ? chainItem.getPayloadHash() : null);
            BigInteger count = chainItem.getTraceEventCount() == null ? BigInteger.ZERO : chainItem.getTraceEventCount();
            if (count.compareTo(MAX_EVENTS) > 0 || count.signum() < 0) return;
            verification.setEventsComplete(count.intValue() == events.size());
            Map<String, ChainTraceEventView> eventViews = new HashMap<>();
            for (int index = 0; index < count.intValue(); index++) {
                ChainTraceEventView event = chain.getTraceEvent(traceCode, index);
                if (event == null || event.getEventIdHash() == null) return;
                if(!BigInteger.valueOf(index).equals(event.getIndex()))return;
                if(eventViews.put(event.getEventIdHash().toLowerCase(),event)!=null){verification.setEventsComplete(false);return;}
            }
            for (TraceEventEntity event : events) {
                String key = "TRACE_EVENT_APPEND:" + event.getId();
                ChainTraceEventView actual=eventViews.get(HashUtils.hashBusinessIdHex("TRACE_EVENT:" + event.getId()).toLowerCase());
                hashes.put(key,actual!=null&&actual.getIndex()!=null&&CirculationEvidenceService.eventMatches(traceCode,event,actual,actual.getIndex().longValue())?actual.getPayloadHash():null);
            }
            for (int index = 0; index < verification.getRecords().size(); index++) {
                PublicTraceView.Record record = verification.getRecords().get(index);
                String key = record.getBusinessType();
                if (key.equals("TRACE_EVENT_APPEND")) key += ":" + events.get(index - 2).getId();
                String actual = hashes.get(key);
                record.setChainPayloadHash(actual);
                record.setPayloadHashMatched(actual != null && actual.equalsIgnoreCase(expected.get(key)));
                record.setVerificationSource("LIVE_CHAIN");
            }
            verification.setBatchPayloadHashMatched(verification.getRecords().get(0).isPayloadHashMatched());
            verification.setItemPayloadHashMatched(verification.getRecords().get(1).isPayloadHashMatched());
            verification.setVerificationSource("LIVE_CHAIN");
        } catch (Exception ex) {
            // 节点不可用时保留业务查询能力，不用数据库摘要伪装实时核验。
            verification.setVerificationSource("UNAVAILABLE");
        }
    }
}
