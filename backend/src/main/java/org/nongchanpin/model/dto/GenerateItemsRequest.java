package org.nongchanpin.model.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateItemsRequest {
    @NotNull
    @Min(1)
    private Long batchId;
    @NotNull
    @Min(1)
    @Max(100)
    private Integer count = 1;
}
