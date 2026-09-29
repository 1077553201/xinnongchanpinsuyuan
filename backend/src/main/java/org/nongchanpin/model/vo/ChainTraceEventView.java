package org.nongchanpin.model.vo;

import java.math.BigInteger;
import lombok.Data;

@Data
public class ChainTraceEventView {
    private String itemCodeHash;
    private BigInteger index;
    private String eventIdHash;
    private BigInteger eventType;
    private String payloadHash;
    private BigInteger occurredAt;
    private BigInteger recordedAt;
    private String operator;
}
