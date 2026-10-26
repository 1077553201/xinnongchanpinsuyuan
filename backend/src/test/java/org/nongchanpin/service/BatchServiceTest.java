package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.model.command.RegisterBatchCommand;
import org.nongchanpin.model.dto.CreateBatchRequest;
import org.nongchanpin.model.dto.CloseBatchRequest;
import java.time.LocalDate;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.ProductEntity;
import org.nongchanpin.model.vo.ChainTxResult;
import org.nongchanpin.chain.ChainStatus;

class BatchServiceTest {
    @Test
    void inactiveOrganizationCannotCreateBatchOrSendChainTransaction() {
        ProductMapper products = mock(ProductMapper.class);
        ProductBatchMapper batches = mock(ProductBatchMapper.class);
        ChainService chain = mock(ChainService.class);
        ChainTransactionService transactions = mock(ChainTransactionService.class);
        ProductEntity product = new ProductEntity(); product.setOrganizationId(1L);
        when(products.findById(1L)).thenReturn(product);
        BatchService service = new BatchService(products, batches, chain, transactions, new ChainWriteSupport(transactions), new PayloadService(), new ContractConfig());
        CreateBatchRequest request = new CreateBatchRequest(); request.setProductId(1L); request.setOrganizationId(1L);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verifyNoInteractions(batches, chain, transactions);
    }
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
        product.setOrganizationId(1L);
        product.setProductCode("P001");
        when(productMapper.findById(1L)).thenReturn(product);
        when(productMapper.lockActiveOrganization(1L)).thenReturn(1L);
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
        request.setPlantDate(LocalDate.of(2026, 3, 1));
        request.setHarvestDate(LocalDate.of(2026, 9, 1));

        ProductBatchEntity created = service.create(request);

        assertEquals("B001", created.getBatchCode());
        verify(chainService).registerBatch(any(RegisterBatchCommand.class));
        verify(batchMapper).updateChainStatus(9L, "CONFIRMED");
    }

    @Test
    void rejectsWrongOrganizationAndReversedDatesBeforeWriting() {
        ProductMapper products = mock(ProductMapper.class);
        ProductBatchMapper batches = mock(ProductBatchMapper.class);
        ChainService chain = mock(ChainService.class);
        ChainTransactionService transactions = mock(ChainTransactionService.class);
        BatchService service = new BatchService(products, batches, chain, transactions,
                new ChainWriteSupport(transactions), new PayloadService(), new ContractConfig());
        ProductEntity product = new ProductEntity();
        product.setOrganizationId(1L);
        when(products.findById(1L)).thenReturn(product);
        when(products.lockActiveOrganization(1L)).thenReturn(1L);
        CreateBatchRequest request = new CreateBatchRequest();
        request.setProductId(1L);
        request.setOrganizationId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setOrganizationId(1L);
        request.setPlantDate(LocalDate.of(2026, 10, 2));
        request.setHarvestDate(LocalDate.of(2026, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verifyNoInteractions(batches, chain, transactions);
    }

    @Test
    void rejectsRepeatedCloseBeforeSubmittingAnotherTransaction() {
        ProductBatchMapper batches = mock(ProductBatchMapper.class);
        ChainService chain = mock(ChainService.class);
        ChainTransactionService transactions = mock(ChainTransactionService.class);
        BatchService service = new BatchService(mock(ProductMapper.class), batches, chain, transactions,
                new ChainWriteSupport(transactions), new PayloadService(), new ContractConfig());
        ProductBatchEntity batch = new ProductBatchEntity();
        batch.setStatus("CLOSED");
        when(batches.findById(1L)).thenReturn(batch);
        CloseBatchRequest request = new CloseBatchRequest();
        request.setStatus(3);
        assertThrows(IllegalArgumentException.class, () -> service.close(1L, request));
        verifyNoInteractions(chain, transactions);
    }
}
