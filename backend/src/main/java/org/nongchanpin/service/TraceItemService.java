package org.nongchanpin.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.TraceItemMapper;
import org.nongchanpin.model.command.BindItemCommand;
import org.nongchanpin.model.dto.GenerateItemsRequest;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.TraceItemEntity;
import org.nongchanpin.util.HashUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TraceItemService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TraceItemMapper traceItemMapper;
    private final ProductBatchMapper batchMapper;
    private final ChainService chainService;
    private final ChainTransactionService chainTransactionService;
    private final ChainWriteSupport chainWriteSupport;
    private final PayloadService payloadService;
    private final ContractConfig contractConfig;

    @Value("${public.traceBaseUrl:http://127.0.0.1:5173/t}")
    private String publicTraceBaseUrl;

    @Transactional
    public List<TraceItemEntity> generate(GenerateItemsRequest request) {
        ProductBatchEntity batch = batchMapper.findById(request.getBatchId());
        if (batch == null) {
            throw new IllegalArgumentException("batch not found");
        }
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw new IllegalArgumentException("only active batches can generate items");
        }
        if (request.getCount() == null || request.getCount() < 1 || request.getCount() > 100) {
            throw new IllegalArgumentException("count must be between 1 and 100");
        }
        List<TraceItemEntity> items = new ArrayList<>();
        if(traceItemMapper.packingBatchCount(request.getBatchId())>0)throw new IllegalArgumentException("该批次来自实际包装，请从采收包装的赋码入口办理，以免超过来源件数。");
        for (int i = 0; i < request.getCount(); i++) {
            items.add(createOne(batch));
        }
        return items;
    }

    public List<TraceItemEntity> findAll() {
        List<TraceItemEntity> items=traceItemMapper.findAll();
        for(TraceItemEntity item:items) {
            org.nongchanpin.model.entity.PackingTraceModels.Bridge bridge=traceItemMapper.packingSource(item.getId());
            if(bridge!=null)item.setPackingId(bridge.getPackingId());
        }
        return items;
    }

    public TraceItemEntity get(Long id) {
        TraceItemEntity item = traceItemMapper.findById(id);
        if (item == null) {
            throw new IllegalArgumentException("item not found");
        }
        return item;
    }

    private TraceItemEntity createOne(ProductBatchEntity batch) {
        String traceCode = nextUniqueTraceCode(batch.getBatchCode());
        String traceCodeHash = HashUtils.hashBusinessIdPlainHex(traceCode);
        TraceItemEntity item = new TraceItemEntity();
        item.setBatchId(batch.getId());
        item.setTraceCodeHash(traceCodeHash);
        item.setTraceCodeEncrypted(traceCodeHash);
        item.setTraceCodePlain(traceCode);
        item.setTraceUrl(publicTraceBaseUrl + "/" + traceCode);
        item.setQrStatus("GENERATED");
        item.setNfcStatus("UNBOUND");
        item.setChainStatus("PENDING");
        item.setStatus("ACTIVE");
        traceItemMapper.insert(item);

        Map<String, Object> payload = payloadService.map();
        payload.put("traceCodeHash", traceCodeHash);
        payload.put("batchCode", batch.getBatchCode());
        payload.put("traceUrl", item.getTraceUrl());
        String payloadHash = payloadService.payloadHashWithPrefix(payload);
        ChainTransactionEntity tx = chainTransactionService.createPending("ITEM_BIND", item.getId(),
                HashUtils.hashBusinessIdHex(traceCode), contractConfig.getAgriTraceAddress(),
                "ItemBound", payloadHash, payloadService.requestHash(payload));

        BindItemCommand command = new BindItemCommand();
        command.setItemCode(traceCode);
        command.setBatchCode(batch.getBatchCode());
        command.setPayload(payload);
        String chainStatus = chainWriteSupport.submit(tx, () -> chainService.bindItem(command));
        traceItemMapper.updateChainStatus(item.getId(), chainStatus);
        return traceItemMapper.findById(item.getId());
    }

    private String nextUniqueTraceCode(String batchCode) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = batchCode + "-" + randomToken(10);
            if (traceItemMapper.countByTraceCodeHash(HashUtils.hashBusinessIdPlainHex(code)) == 0) {
                return code;
            }
        }
        throw new IllegalStateException("failed to generate unique trace code");
    }

    private String randomToken(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
