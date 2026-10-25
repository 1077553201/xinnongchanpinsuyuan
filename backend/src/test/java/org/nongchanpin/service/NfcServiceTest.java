package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.nongchanpin.chain.ChainService;
import org.nongchanpin.config.ContractConfig;
import org.nongchanpin.mapper.NfcTagMapper;
import org.nongchanpin.mapper.TraceItemMapper;
import org.nongchanpin.model.dto.BindNfcRequest;
import org.nongchanpin.model.dto.DisableNfcRequest;
import org.nongchanpin.model.entity.NfcTagEntity;
import org.nongchanpin.model.entity.TraceItemEntity;

class NfcServiceTest {
    @Test
    void packingTagCannotBypassItsDedicatedDisableWorkflow() {
        NfcTagMapper tags=mock(NfcTagMapper.class);
        TraceItemMapper items=mock(TraceItemMapper.class);
        ChainService chain=mock(ChainService.class);
        ChainTransactionService transactions=mock(ChainTransactionService.class);
        NfcService service=new NfcService(tags,items,chain,transactions,new ChainWriteSupport(transactions),new PayloadService(),new ContractConfig());
        NfcTagEntity tag=new NfcTagEntity();tag.setItemId(40L);tag.setStatus("ACTIVE");
        when(tags.findById(7L)).thenReturn(tag);
        when(items.packingSource(40L)).thenReturn(new org.nongchanpin.model.entity.PackingTraceModels.Bridge());
        assertThrows(IllegalArgumentException.class,()->service.disable(7L,new DisableNfcRequest()));
        verifyNoInteractions(chain,transactions);verify(tags,never()).updateStatus(any(),any(),any());
    }
    @Test
    void existingBindingCannotBeReplacedByAnotherTag() {
        NfcTagMapper tags = mock(NfcTagMapper.class);
        TraceItemMapper items = mock(TraceItemMapper.class);
        ChainService chain = mock(ChainService.class);
        ChainTransactionService transactions = mock(ChainTransactionService.class);
        NfcService service = new NfcService(tags, items, chain, transactions, new ChainWriteSupport(transactions), new PayloadService(), new ContractConfig());
        TraceItemEntity item = new TraceItemEntity(); item.setId(1L); item.setStatus("ACTIVE");
        when(items.findById(1L)).thenReturn(item);
        when(tags.findLatestByItemId(1L)).thenReturn(new NfcTagEntity());
        BindNfcRequest request = new BindNfcRequest(); request.setItemId(1L); request.setNfcUid("new-tag");
        assertThrows(IllegalArgumentException.class, () -> service.bind(request));
        verifyNoInteractions(chain, transactions);
    }

    @Test
    void disabledTagCannotSubmitAnotherDisableTransaction() {
        NfcTagMapper tags = mock(NfcTagMapper.class);
        ChainService chain = mock(ChainService.class);
        ChainTransactionService transactions = mock(ChainTransactionService.class);
        NfcService service = new NfcService(tags, mock(TraceItemMapper.class), chain, transactions, new ChainWriteSupport(transactions), new PayloadService(), new ContractConfig());
        NfcTagEntity tag = new NfcTagEntity(); tag.setStatus("DISABLED");
        when(tags.findById(1L)).thenReturn(tag);
        assertThrows(IllegalArgumentException.class, () -> service.disable(1L, new DisableNfcRequest()));
        verifyNoInteractions(chain, transactions);
    }
}
