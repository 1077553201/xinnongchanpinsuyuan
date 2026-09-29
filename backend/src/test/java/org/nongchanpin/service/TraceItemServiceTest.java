package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.chain.ChainStatus;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.TraceItemMapper;
import org.nongchanpin.model.command.BindItemCommand;
import org.nongchanpin.model.dto.GenerateItemsRequest;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.TraceItemEntity;
import org.nongchanpin.model.vo.ChainTxResult;

class TraceItemServiceTest {
    @Test
    void generateCreatesUniqueTraceCodeAndCallsBindItem() {
        TraceItemMapper itemMapper = mock(TraceItemMapper.class);
        ProductBatchMapper batchMapper = mock(ProductBatchMapper.class);
        ChainService chainService = mock(ChainService.class);
        ChainTransactionService txService = mock(ChainTransactionService.class);
        ChainWriteSupport writeSupport = new ChainWriteSupport(txService);
        ContractConfig contractConfig = new ContractConfig();
        contractConfig.setAgriTraceAddress("0xcontract");

        ProductBatchEntity batch = new ProductBatchEntity();
        batch.setId(7L);
        batch.setBatchCode("B001");
        when(batchMapper.findById(7L)).thenReturn(batch);
        when(itemMapper.countByTraceCodeHash(any())).thenReturn(0);
        doAnswer(invocation -> {
            TraceItemEntity item = invocation.getArgument(0);
            item.setId(11L);
            return 1;
        }).when(itemMapper).insert(any(TraceItemEntity.class));
        when(itemMapper.findById(11L)).thenAnswer(invocation -> {
            TraceItemEntity item = new TraceItemEntity();
            item.setId(11L);
            item.setBatchId(7L);
            item.setTraceCodeHash("hash");
            item.setChainStatus("CONFIRMED");
            return item;
        });
        ChainTransactionEntity pending = new ChainTransactionEntity();
        pending.setId(1L);
        when(txService.createPending(eq("ITEM_BIND"), eq(11L), any(), eq("0xcontract"),
                eq("ItemBound"), any(), any())).thenReturn(pending);
        ChainTxResult chainResult = new ChainTxResult();
        chainResult.setChainStatus(ChainStatus.CONFIRMED);
        when(chainService.bindItem(any(BindItemCommand.class))).thenReturn(chainResult);

        TraceItemService service = new TraceItemService(itemMapper, batchMapper, chainService, txService,
                writeSupport, new PayloadService(), contractConfig);
        GenerateItemsRequest request = new GenerateItemsRequest();
        request.setBatchId(7L);
        request.setCount(1);

        List<TraceItemEntity> items = service.generate(request);

        assertEquals(1, items.size());
        assertNotNull(items.get(0).getTraceCodeHash());
        verify(chainService).bindItem(any(BindItemCommand.class));
        verify(itemMapper).updateChainStatus(11L, "CONFIRMED");
    }
}
