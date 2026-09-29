package org.nongchanpin.service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainStatus;
import org.nongchanpin.mapper.ChainTransactionMapper;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.vo.ChainTxResult;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChainTransactionService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ChainTransactionMapper chainTransactionMapper;

    public ChainTransactionEntity createPending(String businessType, Long businessId, String businessIdHash,
            String contractAddress, String eventName, String payloadHash, String requestHash) {
        ChainTransactionEntity tx = new ChainTransactionEntity();
        tx.setBusinessType(businessType);
        tx.setBusinessId(businessId);
        tx.setBusinessIdHash(businessIdHash);
        tx.setChainStatus(ChainStatus.PENDING.name());
        tx.setContractAddress(contractAddress);
        tx.setEventName(eventName);
        tx.setPayloadHash(payloadHash);
        tx.setRequestHash(requestHash);
        tx.setSubmittedAt(LocalDateTime.now());
        chainTransactionMapper.insert(tx);
        return tx;
    }

    public ChainTransactionEntity markConfirmed(Long id, ChainTxResult result) {
        return updateFromResult(id, ChainStatus.CONFIRMED, result, null);
    }

    public ChainTransactionEntity markFailed(Long id, ChainTxResult result) {
        return updateFromResult(id, ChainStatus.FAILED, result, result.getErrorMessage());
    }

    public ChainTransactionEntity markChainDisabled(Long id, ChainTxResult result) {
        return updateFromResult(id, ChainStatus.CHAIN_DISABLED, result, result.getErrorMessage());
    }

    public ChainTransactionEntity completeFromResult(ChainTransactionEntity pending, ChainTxResult result) {
        if (result.getContractAddress() == null) {
            result.setContractAddress(pending.getContractAddress());
        }
        if (result.getEventName() == null) {
            result.setEventName(pending.getEventName());
        }
        if (result.getPayloadHash() == null) {
            result.setPayloadHash(pending.getPayloadHash());
        }
        if (result.getChainStatus() == ChainStatus.CONFIRMED) {
            return markConfirmed(pending.getId(), result);
        }
        if (result.getChainStatus() == ChainStatus.CHAIN_DISABLED) {
            return markChainDisabled(pending.getId(), result);
        }
        return markFailed(pending.getId(), result);
    }

    public ChainTransactionEntity findLatestByBusiness(String businessType, Long businessId) {
        return chainTransactionMapper.findLatestByBusiness(businessType, businessId);
    }

    private ChainTransactionEntity updateFromResult(Long id, ChainStatus status, ChainTxResult result, String error) {
        ChainTransactionEntity tx = new ChainTransactionEntity();
        tx.setId(id);
        tx.setChainStatus(status.name());
        tx.setTxHash(result.getTxHash());
        tx.setBlockNumber(toLong(result.getBlockNumber()));
        tx.setReceiptStatus(result.getReceiptStatus());
        tx.setContractAddress(result.getContractAddress());
        tx.setEventName(result.getEventName());
        tx.setPayloadHash(result.getPayloadHash());
        tx.setChainOperator(result.getChainOperator());
        tx.setConfirmedAt(toLocalDateTime(result.getConfirmedAt()));
        tx.setErrorMessage(error);
        chainTransactionMapper.updateResult(tx);
        return tx;
    }

    private Long toLong(BigInteger value) {
        return value == null ? null : value.longValue();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? LocalDateTime.now() : LocalDateTime.ofInstant(instant, ZONE_ID);
    }
}
