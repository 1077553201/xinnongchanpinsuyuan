package org.nongchanpin.model.vo;

import java.math.BigInteger;
import java.util.List;
import lombok.Data;

@Data
public class ChainHealthResult {
    private String groupId;
    private List<String> peers;
    private String contractAddress;
    private boolean chainEnabled;
    private String accountAddress;
    private Boolean writerStatus;
    private BigInteger latestBlock;
    private String status;
    private String errorMessage;
}
