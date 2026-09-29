package org.nongchanpin.model.vo;

import java.math.BigInteger;
import java.time.Instant;
import lombok.Data;
import org.nongchanpin.chain.ChainStatus;

@Data
public class ChainTxResult {
    private ChainStatus chainStatus;
    private boolean success;
    private String txHash;
    private BigInteger blockNumber;
    private String receiptStatus;
    private String contractAddress;
    private String eventName;
    private String businessIdHash;
    private String payloadHash;
    private String chainOperator;
    private Instant submittedAt;
    private Instant confirmedAt;
    private String errorMessage;

    public static ChainTxResult disabled(String contractAddress, String message) {
        ChainTxResult result = new ChainTxResult();
        result.setChainStatus(ChainStatus.CHAIN_DISABLED);
        result.setContractAddress(contractAddress);
        result.setSubmittedAt(Instant.now());
        result.setErrorMessage(message);
        return result;
    }
}
