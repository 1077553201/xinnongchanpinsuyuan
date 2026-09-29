package org.nongchanpin.model.vo;

import java.math.BigInteger;
import lombok.Data;

@Data
public class ChainBatchView {
    private String batchIdHash;
    private boolean exists;
    private String productIdHash;
    private String payloadHash;
    private BigInteger status;
    private BigInteger startedAt;
    private BigInteger closedAt;
    private String closeReasonHash;
    private String operator;
    private BigInteger traceEventCount;
    private BigInteger sensorWindowCount;
}
