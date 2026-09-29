package org.nongchanpin.service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.constants.AgriTraceCodes;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.TraceEventMapper;
import org.nongchanpin.mapper.TraceItemMapper;
import org.nongchanpin.model.command.AppendTraceEventCommand;
import org.nongchanpin.model.dto.CreateTraceEventRequest;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.TraceEventEntity;
import org.nongchanpin.model.entity.TraceItemEntity;
import org.nongchanpin.util.HashUtils;
import org.nongchanpin.util.TimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TraceEventService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final TraceEventMapper traceEventMapper;
    private final TraceItemMapper traceItemMapper;
    private final ProductBatchMapper batchMapper;
    private final ChainService chainService;
    private final ChainTransactionService chainTransactionService;
    private final ChainWriteSupport chainWriteSupport;
    private final PayloadService payloadService;
    private final ContractConfig contractConfig;

    @Transactional
    public TraceEventEntity append(Long itemId, CreateTraceEventRequest request) {
        TraceItemEntity item = traceItemMapper.findById(itemId);
        if (item == null) {
            throw new IllegalArgumentException("item not found");
        }
        ProductBatchEntity batch = batchMapper.findById(item.getBatchId());
        Map<String, Object> payload = payloadService.map();
        payload.put("traceCodeHash", item.getTraceCodeHash());
        payload.put("eventType", request.getEventType());
        payload.put("eventTime", request.getEventTime());
        payload.put("location", request.getLocation());
        payload.put("operatorName", request.getOperatorName());
        payload.put("payload", request.getPayload());
        String canonical = payloadService.canonicalJson(payload);

        TraceEventEntity event = new TraceEventEntity();
        event.setItemId(item.getId());
        event.setBatchId(item.getBatchId());
        event.setEventType(request.getEventType().trim().toUpperCase());
        event.setEventTime(request.getEventTime());
        event.setLocation(request.getLocation());
        event.setOperatorName(request.getOperatorName());
        event.setCanonicalPayloadHash(payloadService.payloadHash(canonical));
        event.setEventHash(HashUtils.hashCanonicalTextPlainHex(canonical + item.getId()));
        event.setPayload(canonical);
        event.setChainStatus("PENDING");
        event.setStatus("ACTIVE");
        traceEventMapper.insert(event);

        String eventBusinessId = "TRACE_EVENT:" + event.getId();
        ChainTransactionEntity tx = chainTransactionService.createPending("TRACE_EVENT_APPEND", event.getId(),
                HashUtils.hashBusinessIdHex(eventBusinessId), contractConfig.getAgriTraceAddress(),
                "TraceEventAppended", "0x" + event.getCanonicalPayloadHash(), payloadService.requestHash(payload));

        AppendTraceEventCommand command = new AppendTraceEventCommand();
        command.setItemCode(itemCodeForChain(item));
        command.setEventId(eventBusinessId);
        command.setEventType(AgriTraceCodes.eventTypeCode(request.getEventType()));
        command.setPayload(payload);
        command.setOccurredAt(TimeUtils.epochSeconds(request.getEventTime(), ZONE_ID).longValue());
        String chainStatus = chainWriteSupport.submit(tx, () -> chainService.appendTraceEvent(command));
        traceEventMapper.updateChainStatus(event.getId(), chainStatus);
        if (batch != null && "RECALL".equals(event.getEventType())) {
            traceItemMapper.updateStatus(item.getId(), chainStatus, "RECALL");
        }
        return event;
    }

    public List<TraceEventEntity> findByItemId(Long itemId) {
        return traceEventMapper.findByItemId(itemId);
    }

    private String itemCodeForChain(TraceItemEntity item) {
        return item.getTraceCodePlain() == null || item.getTraceCodePlain().trim().isEmpty()
                ? "0x" + item.getTraceCodeHash()
                : item.getTraceCodePlain();
    }
}
