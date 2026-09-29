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
public class AgriTraceBindNfcTagInputBO {
  private byte[] itemCodeHash;

  private byte[] nfcUidHash;

  private byte[] payloadHash;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(itemCodeHash);
    args.add(nfcUidHash);
    args.add(payloadHash);
    return args;
  }
}
