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
public class AgriTraceGetTraceEventInputBO {
  private byte[] itemCodeHash;

  private BigInteger index;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(itemCodeHash);
    args.add(index);
    return args;
  }
}
