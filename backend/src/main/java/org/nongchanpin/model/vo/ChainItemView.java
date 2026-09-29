package org.nongchanpin.model.vo;

import java.math.BigInteger;
import lombok.Data;

@Data
public class ChainItemView {
    private String itemCodeHash;
    private boolean exists;
    private String batchIdHash;
    private String payloadHash;
    private BigInteger status;
    private String nfcUidHash;
    private BigInteger createdAt;
    private BigInteger updatedAt;
    private String statusReasonHash;
    private String operator;
    private BigInteger traceEventCount;
}
