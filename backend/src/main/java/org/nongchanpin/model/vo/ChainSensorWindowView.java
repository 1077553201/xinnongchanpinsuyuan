package org.nongchanpin.model.vo;

import java.math.BigInteger;
import lombok.Data;

@Data
public class ChainSensorWindowView {
    private String batchIdHash;
    private BigInteger index;
    private String windowIdHash;
    private String deviceCodeHash;
    private String payloadHash;
    private BigInteger windowStart;
    private BigInteger windowEnd;
    private BigInteger recordedAt;
    private String operator;
}
