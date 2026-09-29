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
public class AgriTraceGetSensorWindowInputBO {
  private byte[] batchIdHash;

  private BigInteger index;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(batchIdHash);
    args.add(index);
    return args;
  }
}
