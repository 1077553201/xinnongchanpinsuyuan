package org.nongchanpin.model.bo;

import java.lang.Boolean;
import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgriTraceSetWriterInputBO {
  private String writer;

  private Boolean allowed;

  public List<Object> toArgs() {
    List args = new ArrayList();
    args.add(writer);
    args.add(allowed);
    return args;
  }
}
