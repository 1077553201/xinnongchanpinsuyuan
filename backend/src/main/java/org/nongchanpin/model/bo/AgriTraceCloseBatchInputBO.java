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
public class AgriTraceCloseBatchInputBO {
  private byte[] batchIdHash;

  private BigInteger status;

  private byte[] reasonHash;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(batchIdHash);
    args.add(status);
    args.add(reasonHash);
    return args;
  }
}
