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
public class AgriTraceAppendTraceEventInputBO {
  private byte[] itemCodeHash;

  private byte[] eventIdHash;

  private BigInteger eventType;

  private byte[] payloadHash;

  private BigInteger occurredAt;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(itemCodeHash);
    args.add(eventIdHash);
    args.add(eventType);
    args.add(payloadHash);
    args.add(occurredAt);
    return args;
  }
}
