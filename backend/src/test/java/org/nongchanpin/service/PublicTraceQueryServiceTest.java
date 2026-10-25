package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.IotDataWindowMapper;
import org.nongchanpin.mapper.NfcTagMapper;
import org.nongchanpin.mapper.ProductBatchMapper;
import org.nongchanpin.mapper.ProductMapper;
import org.nongchanpin.mapper.QualityReportMapper;
import org.nongchanpin.mapper.TraceEventMapper;
import org.nongchanpin.mapper.TraceItemMapper;
import org.nongchanpin.model.entity.ProductBatchEntity;
import org.nongchanpin.model.entity.ProductEntity;
import org.nongchanpin.model.entity.TraceItemEntity;
import org.nongchanpin.model.vo.PublicTraceView;
import org.nongchanpin.util.HashUtils;

class PublicTraceQueryServiceTest {
    @Test
    void publicTraceCombinesProductBatchItemEventsAndChainState() {
        TraceItemMapper itemMapper = mock(TraceItemMapper.class);
        ProductBatchMapper batchMapper = mock(ProductBatchMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        NfcTagMapper nfcMapper = mock(NfcTagMapper.class);
        TraceEventMapper eventMapper = mock(TraceEventMapper.class);
        QualityReportMapper qualityMapper = mock(QualityReportMapper.class);
        IotDataWindowMapper windowMapper = mock(IotDataWindowMapper.class);
        ChainTransactionService txService = mock(ChainTransactionService.class);
        ContractConfig contractConfig = new ContractConfig();
        contractConfig.setAgriTraceAddress("0xcontract");

        String traceCode = "TRACE-001";
        TraceItemEntity item = new TraceItemEntity();
        item.setId(1L);
        item.setBatchId(2L);
        item.setTraceCodeHash(HashUtils.hashBusinessIdPlainHex(traceCode));
        item.setChainStatus("CONFIRMED");
        when(itemMapper.findByTraceCodeHash(item.getTraceCodeHash())).thenReturn(item);
        ProductBatchEntity batch = new ProductBatchEntity();
        batch.setId(2L);
        batch.setProductId(3L);
        batch.setBatchCode("B001");
        when(batchMapper.findById(2L)).thenReturn(batch);
        ProductEntity product = new ProductEntity();
        product.setId(3L);
        product.setProductCode("P001");
        product.setProductName("Apple");
        when(productMapper.findById(3L)).thenReturn(product);
        when(eventMapper.findByItemId(1L)).thenReturn(Collections.emptyList());
        when(qualityMapper.findForTrace(1L, 2L)).thenReturn(Collections.emptyList());
        when(windowMapper.findRecentForBatch(2L)).thenReturn(Collections.emptyList());

        PublicTraceQueryService service = new PublicTraceQueryService(itemMapper, batchMapper, productMapper,
                nfcMapper, eventMapper, qualityMapper, windowMapper, txService, new PayloadService(), contractConfig, mock(PublicChainVerifier.class), mock(ItemCirculationService.class), mock(CirculationEvidenceService.class), mock(SourceQualityService.class));

        PublicTraceView view = service.getTrace(traceCode);

        assertEquals("P001", view.getProduct().getProductCode());
        assertEquals("B001", view.getBatch().getBatchCode());
        assertEquals("0xcontract", view.getChainVerification().getContractAddress());
        verify(windowMapper).findRecentForBatch(2L);
        verify(windowMapper, never()).findRecentActive();
    }
}
