package org.nongchanpin.model.bo;

import java.lang.Object;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgriTraceRegisterBatchInputBO {
  private byte[] batchIdHash;

  private byte[] productIdHash;

  private byte[] payloadHash;

  private BigInteger startedAt;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(batchIdHash);
    args.add(productIdHash);
    args.add(payloadHash);
    args.add(startedAt);
    return args;
  }
}
