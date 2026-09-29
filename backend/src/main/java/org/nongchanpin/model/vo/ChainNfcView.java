package org.nongchanpin.model.vo;

import java.math.BigInteger;
import lombok.Data;

@Data
public class ChainNfcView {
    private String nfcUidHash;
    private boolean exists;
    private String itemCodeHash;
    private String payloadHash;
    private BigInteger status;
    private String reasonHash;
    private BigInteger boundAt;
    private BigInteger updatedAt;
    private String operator;
}
