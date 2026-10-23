package org.nongchanpin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.IotDataWindowMapper;
import org.nongchanpin.mapper.NfcTagMapper;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.mapper.QualityReportMapper;
import org.nongchanpin.mapper.TraceEventMapper;
import org.nongchanpin.mapper.TraceItemMapper;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.IotDataWindowEntity;
import org.nongchanpin.model.entity.NfcTagEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.ProductEntity;
import org.nongchanpin.model.entity.QualityReportEntity;
import org.nongchanpin.model.entity.TraceEventEntity;
import org.nongchanpin.model.entity.TraceItemEntity;
import org.nongchanpin.model.vo.PublicTraceView;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicTraceQueryService {
    private final TraceItemMapper traceItemMapper;
    private final ProductBatchMapper batchMapper;
    private final ProductMapper productMapper;
    private final NfcTagMapper nfcTagMapper;
    private final TraceEventMapper traceEventMapper;
    private final QualityReportMapper qualityReportMapper;
    private final IotDataWindowMapper iotDataWindowMapper;
    private final ChainTransactionService chainTransactionService;
    private final PayloadService payloadService;
    private final ContractConfig contractConfig;
    private final PublicChainVerifier publicChainVerifier;

    public PublicTraceView getTrace(String traceCode) {
        String traceCodeHash = HashUtils.hashBusinessIdPlainHex(traceCode);
        TraceItemEntity item = traceItemMapper.findByTraceCodeHash(traceCodeHash);
        if (item == null) {
            throw new IllegalArgumentException("trace code not found");
        }
        ProductBatchEntity batch = batchMapper.findById(item.getBatchId());
        ProductEntity product = batch == null ? null : productMapper.findById(batch.getProductId());
        NfcTagEntity nfc = nfcTagMapper.findLatestByItemId(item.getId());
        List<TraceEventEntity> events = traceEventMapper.findByItemId(item.getId());

        PublicTraceView view = new PublicTraceView();
        view.setProduct(toProduct(product));
        view.setBatch(toBatch(batch));
        view.setItem(toItem(item));
        view.setProductionSource(PackingTraceService.publicSource(traceItemMapper.packingSource(item.getId())));
        view.setNfc(toNfc(nfc));
        view.setTimeline(toEvents(events));
        view.setQualityReports(toQualities(qualityReportMapper.findForTrace(item.getId(), item.getBatchId())));
        view.setEnvironmentSummary(toEnvironment(iotDataWindowMapper.findRecentForBatch(item.getBatchId())));
        PublicTraceView.ChainVerification verification = verify(product, batch, item, events);
        Map<String, String> expected = new LinkedHashMap<>();
        if (batch != null && product != null) expected.put("BATCH_REGISTER", recomputeBatchPayloadHash(product, batch));
        expected.put("ITEM_BIND", recomputeItemPayloadHash(batch, item));
        for (TraceEventEntity event : events) expected.put("TRACE_EVENT_APPEND:" + event.getId(), recomputeEventPayloadHash(item, event));
        if (batch != null && product != null && !"NOT_SUBMITTED".equals(item.getChainStatus())) publicChainVerifier.verify(traceCode, batch, events, verification, expected);
        else verification.setVerificationSource("UNAVAILABLE");
        view.setChainVerification(verification);
        if (Integer.valueOf(3).equals(verification.getChainBatchStatus()) && view.getBatch() != null) view.getBatch().setStatus("RECALL");
        if (Integer.valueOf(4).equals(verification.getChainItemStatus())) view.getItem().setStatus("RECALL");
        PublicTraceView.AiStatus ai = new PublicTraceView.AiStatus();
        ai.setAvailable(false);
        ai.setProvider("disabled");
        view.setAi(ai);
        return view;
    }

    private PublicTraceView.Product toProduct(ProductEntity entity) {
        if (entity == null) {
            return null;
        }
        PublicTraceView.Product product = new PublicTraceView.Product();
        product.setProductCode(entity.getProductCode());
        product.setProductName(entity.getProductName());
        product.setCategory(entity.getCategory());
        product.setVariety(entity.getVariety());
        product.setOriginPlace(entity.getOriginPlace());
        product.setDescription(entity.getDescription());
        return product;
    }

    private PublicTraceView.Batch toBatch(ProductBatchEntity entity) {
        if (entity == null) {
            return null;
        }
        PublicTraceView.Batch batch = new PublicTraceView.Batch();
        batch.setBatchCode(entity.getBatchCode());
        batch.setProductionArea(entity.getProductionArea());
        batch.setPlantDate(String.valueOf(entity.getPlantDate()));
        batch.setHarvestDate(String.valueOf(entity.getHarvestDate()));
        batch.setChainStatus(entity.getChainStatus());
        batch.setStatus(entity.getStatus());
        return batch;
    }

    private PublicTraceView.Item toItem(TraceItemEntity entity) {
        PublicTraceView.Item item = new PublicTraceView.Item();
        item.setTraceCodeHash(entity.getTraceCodeHash());
        item.setTraceUrl(entity.getTraceUrl());
        item.setQrStatus(entity.getQrStatus());
        item.setNfcStatus(entity.getNfcStatus());
        item.setChainStatus(entity.getChainStatus());
        item.setStatus(entity.getStatus());
        return item;
    }

    private PublicTraceView.Nfc toNfc(NfcTagEntity entity) {
        if (entity == null) {
            return null;
        }
        PublicTraceView.Nfc nfc = new PublicTraceView.Nfc();
        nfc.setUidHash(entity.getUidHash());
        nfc.setBindStatus(entity.getBindStatus());
        nfc.setStatus(entity.getStatus());
        return nfc;
    }

    private List<PublicTraceView.Event> toEvents(List<TraceEventEntity> entities) {
        List<PublicTraceView.Event> events = new ArrayList<>();
        for (TraceEventEntity entity : entities) {
            PublicTraceView.Event event = new PublicTraceView.Event();
            event.setEventType(entity.getEventType());
            event.setEventTime(String.valueOf(entity.getEventTime()));
            event.setLocation(entity.getLocation());
            event.setOperatorName(entity.getOperatorName());
            event.setPayloadHash(entity.getCanonicalPayloadHash());
            event.setChainStatus(entity.getChainStatus());
            events.add(event);
        }
        return events;
    }

    private List<PublicTraceView.Quality> toQualities(List<QualityReportEntity> entities) {
        List<PublicTraceView.Quality> qualities = new ArrayList<>();
        for (QualityReportEntity entity : entities) {
            PublicTraceView.Quality quality = new PublicTraceView.Quality();
            quality.setReportNo(entity.getReportNo());
            quality.setAgencyName(entity.getAgencyName());
            quality.setResult(entity.getResult());
            quality.setReportHash(entity.getReportHash());
            quality.setCheckedAt(String.valueOf(entity.getCheckedAt()));
            qualities.add(quality);
        }
        return qualities;
    }

    private List<PublicTraceView.Environment> toEnvironment(List<IotDataWindowEntity> entities) {
        List<PublicTraceView.Environment> windows = new ArrayList<>();
        for (IotDataWindowEntity entity : entities) {
            PublicTraceView.Environment window = new PublicTraceView.Environment();
            window.setDeviceCode(entity.getDeviceCode());
            window.setMetric(entity.getMetric());
            window.setWindowStart(String.valueOf(entity.getWindowStart()));
            window.setWindowEnd(String.valueOf(entity.getWindowEnd()));
            window.setMinValue(String.valueOf(entity.getMinValue()));
            window.setMaxValue(String.valueOf(entity.getMaxValue()));
            window.setAvgValue(String.valueOf(entity.getAvgValue()));
            window.setSampleCount(entity.getSampleCount());
            window.setDataHash(entity.getDataHash());
            window.setChainStatus(entity.getChainStatus());
            windows.add(window);
        }
        return windows;
    }

    private PublicTraceView.ChainVerification verify(ProductEntity product, ProductBatchEntity batch,
            TraceItemEntity item, List<TraceEventEntity> events) {
        PublicTraceView.ChainVerification verification = new PublicTraceView.ChainVerification();
        verification.setContractAddress(contractConfig.getAgriTraceAddress());
        List<PublicTraceView.Record> records = new ArrayList<>();

        ChainTransactionEntity batchTx = null;
        if (batch != null && product != null) {
            batchTx = chainTransactionService.findLatestByBusiness("BATCH_REGISTER", batch.getId());
            String batchHash = recomputeBatchPayloadHash(product, batch);
            PublicTraceView.Record record = toRecord("BATCH_REGISTER", batchTx, batchHash);
            records.add(record);
            verification.setBatchPayloadHashMatched(record.isPayloadHashMatched());
        }

        ChainTransactionEntity itemTx = chainTransactionService.findLatestByBusiness("ITEM_BIND", item.getId());
        String itemHash = recomputeItemPayloadHash(batch, item);
        PublicTraceView.Record itemRecord = toRecord("ITEM_BIND", itemTx, itemHash);
        records.add(itemRecord);
        verification.setItemPayloadHashMatched(itemRecord.isPayloadHashMatched());

        for (TraceEventEntity event : events) {
            ChainTransactionEntity eventTx = chainTransactionService.findLatestByBusiness("TRACE_EVENT_APPEND", event.getId());
            records.add(toRecord("TRACE_EVENT_APPEND", eventTx, "0x" + event.getCanonicalPayloadHash()));
        }
        ChainTransactionEntity latest = itemTx != null ? itemTx : batchTx;
        verification.setLatestTxHash(latest == null ? null : latest.getTxHash());
        verification.setLatestChainStatus(latest == null ? ("NOT_SUBMITTED".equals(item.getChainStatus())?"NOT_SUBMITTED":null) : latest.getChainStatus());
        verification.setRecords(records);
        return verification;
    }

    private String recomputeBatchPayloadHash(ProductEntity product, ProductBatchEntity batch) {
        Map<String, Object> payload = payloadService.map();
        payload.put("batchCode", batch.getBatchCode());
        payload.put("productCode", product.getProductCode());
        payload.put("productionArea", batch.getProductionArea());
        payload.put("plantDate", batch.getPlantDate());
        payload.put("harvestDate", batch.getHarvestDate());
        payload.put("metadata", payloadService.readJson(batch.getBatchMetadata()));
        return payloadService.payloadHashWithPrefix(payload);
    }

    private String recomputeItemPayloadHash(ProductBatchEntity batch, TraceItemEntity item) {
        Map<String, Object> payload = payloadService.map();
        payload.put("traceCodeHash", item.getTraceCodeHash());
        payload.put("batchCode", batch == null ? null : batch.getBatchCode());
        payload.put("traceUrl", item.getTraceUrl());
        return payloadService.payloadHashWithPrefix(payload);
    }

    private String recomputeEventPayloadHash(TraceItemEntity item, TraceEventEntity event) {
        Map<String, Object> payload = payloadService.map();
        payload.put("traceCodeHash", item.getTraceCodeHash());
        payload.put("eventType", event.getEventType());
        payload.put("eventTime", event.getEventTime());
        payload.put("location", event.getLocation());
        payload.put("operatorName", event.getOperatorName());
        Object canonical = payloadService.readJson(event.getPayload());
        payload.put("payload", canonical instanceof Map ? ((Map<?, ?>) canonical).get("payload") : null);
        return payloadService.payloadHashWithPrefix(payload);
    }

    private PublicTraceView.Record toRecord(String businessType, ChainTransactionEntity tx, String expectedPayloadHash) {
        PublicTraceView.Record record = new PublicTraceView.Record();
        record.setBusinessType(businessType);
        if (tx != null) {
            record.setChainStatus(tx.getChainStatus());
            record.setTxHash(tx.getTxHash());
            record.setPayloadHash(tx.getPayloadHash());
            record.setPayloadHashMatched(expectedPayloadHash != null && expectedPayloadHash.equalsIgnoreCase(tx.getPayloadHash()));
        }
        return record;
    }
}
