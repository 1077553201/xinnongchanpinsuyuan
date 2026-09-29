package org.nongchanpin.model.bo;

import java.lang.Object;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgriTraceBindItemInputBO {
  private byte[] itemCodeHash;

  private byte[] batchIdHash;

  private byte[] payloadHash;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(itemCodeHash);
    args.add(batchIdHash);
    args.add(payloadHash);
    return args;
  }
}
