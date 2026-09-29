package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.model.command.RegisterBatchCommand;
import org.nongchanpin.model.dto.CreateBatchRequest;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.ProductEntity;
import org.nongchanpin.model.vo.ChainTxResult;
import org.nongchanpin.chain.ChainStatus;

class BatchServiceTest {
    @Test
    void createPersistsBatchBuildsHashCallsChainAndWritesStatus() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductBatchMapper batchMapper = mock(ProductBatchMapper.class);
        ChainService chainService = mock(ChainService.class);
        ChainTransactionService txService = mock(ChainTransactionService.class);
        ChainWriteSupport writeSupport = new ChainWriteSupport(txService);
        PayloadService payloadService = new PayloadService();
        ContractConfig contractConfig = new ContractConfig();
        contractConfig.setAgriTraceAddress("0xcontract");

        ProductEntity product = new ProductEntity();
        product.setId(1L);
        product.setProductCode("P001");
        when(productMapper.findById(1L)).thenReturn(product);
        doAnswer(invocation -> {
            ProductBatchEntity batch = invocation.getArgument(0);
            batch.setId(9L);
            return 1;
        }).when(batchMapper).insert(any(ProductBatchEntity.class));
        when(batchMapper.findById(9L)).thenAnswer(invocation -> {
            ProductBatchEntity batch = new ProductBatchEntity();
            batch.setId(9L);
            batch.setBatchCode("B001");
            batch.setChainStatus("CONFIRMED");
            return batch;
        });
        ChainTransactionEntity pending = new ChainTransactionEntity();
        pending.setId(99L);
        when(txService.createPending(eq("BATCH_REGISTER"), eq(9L), any(), eq("0xcontract"),
                eq("BatchRegistered"), any(), any())).thenReturn(pending);
        ChainTxResult chainResult = new ChainTxResult();
        chainResult.setChainStatus(ChainStatus.CONFIRMED);
        when(chainService.registerBatch(any(RegisterBatchCommand.class))).thenReturn(chainResult);

        BatchService service = new BatchService(productMapper, batchMapper, chainService, txService,
                writeSupport, payloadService, contractConfig);
        CreateBatchRequest request = new CreateBatchRequest();
        request.setProductId(1L);
        request.setOrganizationId(1L);
        request.setBatchCode("B001");

        ProductBatchEntity created = service.create(request);

        assertEquals("B001", created.getBatchCode());
        verify(chainService).registerBatch(any(RegisterBatchCommand.class));
        verify(batchMapper).updateChainStatus(9L, "CONFIRMED");
    }
}
