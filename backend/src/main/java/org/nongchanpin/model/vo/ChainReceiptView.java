package org.nongchanpin.model.vo;

import java.math.BigInteger;
import java.util.*;
import lombok.Data;

@Data
public class ChainReceiptView {
    private boolean found,inputMatched;
    private String transactionHash,contractAddress,operator,inputHash;
    private BigInteger blockNumber;
    private Integer receiptStatus;
    private List<Map<String,String>> events=new ArrayList<>();
}
