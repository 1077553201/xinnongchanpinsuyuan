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
public class AgriTraceGetSensorWindowCountInputBO {
  private byte[] batchIdHash;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(batchIdHash);
    return args;
  }
}
