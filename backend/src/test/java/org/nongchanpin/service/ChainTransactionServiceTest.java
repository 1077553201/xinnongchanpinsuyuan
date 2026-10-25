package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.nongchanpin.chain.ChainStatus;
import org.nongchanpin.mapper.ChainTransactionMapper;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.vo.ChainTxResult;

class ChainTransactionServiceTest {
    @Test
    void lateFailureCannotOverwriteVerifiedOriginalReceipt() {
        FakeChainTransactionMapper mapper=new FakeChainTransactionMapper();ChainTransactionService service=new ChainTransactionService(mapper);
        ChainTransactionEntity tx=service.createPending("ITEM_BIND",1L,"business","contract","ItemBound","payload","request");
        tx.setChainStatus("CONFIRMED");tx.setTxHash("original-hash");tx.setReceiptStatus("0");tx.setReceiptVerifiedAt(java.time.LocalDateTime.now());
        ChainTxResult late=new ChainTxResult();late.setChainStatus(ChainStatus.FAILED);late.setErrorMessage("late timeout");
        ChainTransactionEntity result=service.completeFromResult(tx,late);
        assertEquals("CONFIRMED",result.getChainStatus());assertEquals("original-hash",result.getTxHash());assertEquals("0",result.getReceiptStatus());
    }
    @Test
    void stateTransitionsFromPendingToConfirmed() {
        FakeChainTransactionMapper mapper = new FakeChainTransactionMapper();
        ChainTransactionService service = new ChainTransactionService(mapper);
        ChainTransactionEntity pending = service.createPending("BATCH_REGISTER", 1L, "0xabc",
                "0xcontract", "BatchRegistered", "0xpayload", "request");

        ChainTxResult result = new ChainTxResult();
        result.setChainStatus(ChainStatus.CONFIRMED);
        result.setTxHash("0xtx");
        result.setBlockNumber(BigInteger.TEN);
        result.setReceiptStatus("0x0");
        result.setContractAddress("0xcontract");
        result.setPayloadHash("0xpayload");

        service.completeFromResult(pending, result);

        assertEquals("CONFIRMED", mapper.store.get(pending.getId()).getChainStatus());
        assertEquals("0xtx", mapper.store.get(pending.getId()).getTxHash());
        assertEquals(10L, mapper.store.get(pending.getId()).getBlockNumber());
    }

    static class FakeChainTransactionMapper implements ChainTransactionMapper {
        private final Map<Long, ChainTransactionEntity> store = new HashMap<>();
        private long nextId = 1L;

        @Override
        public List<ChainTransactionEntity> findRecent(String type, Long id, String status, int limit) { return new ArrayList<>(store.values()); }

        @Override
        public ChainTransactionEntity findById(Long id) { return store.get(id); }
        @Override public ChainTransactionEntity lockReceipt(Long id){return store.get(id);}
        @Override public int recordReceipt(ChainTransactionEntity tx){store.put(tx.getId(),tx);return 1;}

        @Override
        public int insert(ChainTransactionEntity tx) {
            tx.setId(nextId++);
            store.put(tx.getId(), tx);
            return 1;
        }

        @Override
        public int updateResult(ChainTransactionEntity tx) {
            ChainTransactionEntity existing = store.get(tx.getId());
            if(existing.getReceiptVerifiedAt()!=null)return 0;
            existing.setChainStatus(tx.getChainStatus());
            existing.setTxHash(tx.getTxHash());
            existing.setBlockNumber(tx.getBlockNumber());
            existing.setReceiptStatus(tx.getReceiptStatus());
            existing.setPayloadHash(tx.getPayloadHash());
            return 1;
        }

        @Override
        public ChainTransactionEntity findLatestByBusiness(String businessType, Long businessId) {
            return store.values().stream()
                    .filter(tx -> businessType.equals(tx.getBusinessType()) && businessId.equals(tx.getBusinessId()))
                    .reduce((first, second) -> second)
                    .orElse(null);
        }
    }
}
