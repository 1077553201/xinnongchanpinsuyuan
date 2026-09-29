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
public class AgriTraceAppendSensorWindowInputBO {
  private byte[] batchIdHash;

  private byte[] windowIdHash;

  private byte[] deviceCodeHash;

  private byte[] payloadHash;

  private BigInteger windowStart;

  private BigInteger windowEnd;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(batchIdHash);
    args.add(windowIdHash);
    args.add(deviceCodeHash);
    args.add(payloadHash);
    args.add(windowStart);
    args.add(windowEnd);
    return args;
  }
}
