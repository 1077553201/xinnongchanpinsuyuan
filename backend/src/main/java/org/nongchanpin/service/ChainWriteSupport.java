package org.nongchanpin.service;

import java.time.Instant;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.chain.ChainStatus;
import org.nongchanpin.model.entity.ChainTransactionEntity;
import org.nongchanpin.model.vo.ChainTxResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChainWriteSupport {
    private final ChainTransactionService chainTransactionService;

    public String submit(ChainTransactionEntity pending, Supplier<ChainTxResult> writer) {
        ChainTxResult result;
        try {
            result = writer.get();
        } catch (Exception ex) {
            result = new ChainTxResult();
            result.setChainStatus(ChainStatus.FAILED);
            result.setConfirmedAt(Instant.now());
            result.setErrorMessage(ex.getMessage());
        }
        chainTransactionService.completeFromResult(pending, result);
        return result.getChainStatus().name();
    }
}
