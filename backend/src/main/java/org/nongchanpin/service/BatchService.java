package org.nongchanpin.service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.constants.AgriTraceCodes;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.model.dto.CreateBatchRequest;
import org.nongchanpin.model.dto.CloseBatchRequest;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.ProductEntity;
import org.nongchanpin.model.command.RegisterBatchCommand;
import org.nongchanpin.util.HashUtils;
import org.nongchanpin.util.TimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BatchService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ProductMapper productMapper;
    private final ProductBatchMapper batchMapper;
    private final ChainService chainService;
    private final ChainTransactionService chainTransactionService;
    private final ChainWriteSupport chainWriteSupport;
    private final PayloadService payloadService;
    private final ContractConfig contractConfig;

    @Transactional
    public ProductBatchEntity create(CreateBatchRequest request) {
        ProductEntity product = productMapper.findById(request.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("product not found");
        }
        if (!Objects.equals(product.getOrganizationId(), request.getOrganizationId())) {
            throw new IllegalArgumentException("batch organization must match product organization");
        }
        if (request.getPlantDate() != null && request.getHarvestDate() != null
                && request.getHarvestDate().isBefore(request.getPlantDate())) {
            throw new IllegalArgumentException("harvest date must not precede plant date");
        }
        String metadataJson = payloadService.canonicalJson(request.getMetadata());
        ProductBatchEntity batch = new ProductBatchEntity();
        batch.setProductId(request.getProductId());
        batch.setOrganizationId(request.getOrganizationId());
        batch.setBatchCode(request.getBatchCode());
        batch.setProductionArea(request.getProductionArea());
        batch.setPlantDate(request.getPlantDate());
        batch.setHarvestDate(request.getHarvestDate());
        batch.setBatchMetadata(metadataJson);
        batch.setChainStatus("PENDING");
        batch.setStatus("ACTIVE");
        batchMapper.insert(batch);

        Map<String, Object> payload = payloadService.map();
        payload.put("batchCode", batch.getBatchCode());
        payload.put("productCode", product.getProductCode());
        payload.put("productionArea", batch.getProductionArea());
        payload.put("plantDate", batch.getPlantDate());
        payload.put("harvestDate", batch.getHarvestDate());
        payload.put("metadata", request.getMetadata());
        String payloadHash = payloadService.payloadHashWithPrefix(payload);
        ChainTransactionEntity tx = chainTransactionService.createPending("BATCH_REGISTER", batch.getId(),
                HashUtils.hashBusinessIdHex(batch.getBatchCode()), contractConfig.getAgriTraceAddress(),
                "BatchRegistered", payloadHash, payloadService.requestHash(payload));

        RegisterBatchCommand command = new RegisterBatchCommand();
        command.setBatchCode(batch.getBatchCode());
        command.setProductCode(product.getProductCode());
        command.setPayload(payload);
        command.setStartedAt(TimeUtils.epochSeconds(batch.getCreateTime(), ZONE_ID).longValue());
        String chainStatus = chainWriteSupport.submit(tx, () -> chainService.registerBatch(command));
        batchMapper.updateChainStatus(batch.getId(), chainStatus);
        return batchMapper.findById(batch.getId());
    }

    public List<ProductBatchEntity> findAll() {
        return batchMapper.findAll();
    }

    public ProductBatchEntity get(Long id) {
        ProductBatchEntity batch = batchMapper.findById(id);
        if (batch == null) {
            throw new IllegalArgumentException("batch not found");
        }
        return batch;
    }

    @Transactional
    public ProductBatchEntity close(Long id, CloseBatchRequest request) {
        if(batchMapper.packingBatchCount(id)>0)throw new IllegalArgumentException("包装来源批次暂未接入存证及召回流程，请勿通过旧批次入口改变来源状态。");
        ProductBatchEntity batch = get(id);
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw new IllegalArgumentException("only active batches can be closed or recalled");
        }
        if (!AgriTraceCodes.isValidBatchCloseStatus(request.getStatus())) {
            throw new IllegalArgumentException("batch status must be 2(CLOSED) or 3(RECALL)");
        }
        Map<String, Object> reason = payloadService.map();
        reason.put("batchCode", batch.getBatchCode());
        reason.put("status", request.getStatus());
        reason.put("reason", request.getReasonPayload());
        String payloadHash = payloadService.payloadHashWithPrefix(reason);
        ChainTransactionEntity tx = chainTransactionService.createPending("BATCH_CLOSE", batch.getId(),
                HashUtils.hashBusinessIdHex(batch.getBatchCode()), contractConfig.getAgriTraceAddress(),
                "BatchClosed", payloadHash, payloadService.requestHash(reason));

        org.nongchanpin.model.command.CloseBatchCommand command = new org.nongchanpin.model.command.CloseBatchCommand();
        command.setBatchCode(batch.getBatchCode());
        command.setStatus(request.getStatus());
        command.setReasonPayload(reason);
        String chainStatus = chainWriteSupport.submit(tx, () -> chainService.closeBatch(command));
        String status = request.getStatus() == AgriTraceCodes.BATCH_STATUS_RECALL ? "RECALL" : "CLOSED";
        batchMapper.updateStatus(batch.getId(), chainStatus, status);
        return batchMapper.findById(batch.getId());
    }
}
