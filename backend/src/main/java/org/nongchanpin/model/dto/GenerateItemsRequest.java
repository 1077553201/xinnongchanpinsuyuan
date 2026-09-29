package org.nongchanpin.model.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateItemsRequest {
    @NotNull
    private Long batchId;
    @Min(1)
    @Max(100)
    private Integer count = 1;
}
