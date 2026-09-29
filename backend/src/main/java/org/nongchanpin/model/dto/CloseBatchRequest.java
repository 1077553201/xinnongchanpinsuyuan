package org.nongchanpin.model.dto;

import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CloseBatchRequest {
    @NotNull
    private Integer status;
    private Map<String, Object> reasonPayload;
}
