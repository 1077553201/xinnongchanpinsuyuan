package org.nongchanpin.model.dto;

import java.time.LocalDate;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBatchRequest {
    @NotNull
    @Positive
    private Long productId;
    @NotNull
    @Positive
    private Long organizationId;
    @NotBlank
    @Size(max = 64)
    private String batchCode;
    @Size(max = 255)
    private String productionArea;
    private LocalDate plantDate;
    private LocalDate harvestDate;
    private Map<String, Object> metadata;
}
