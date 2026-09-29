package org.nongchanpin.model.dto;

import java.time.LocalDate;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBatchRequest {
    @NotNull
    private Long productId;
    @NotNull
    private Long organizationId;
    @NotBlank
    private String batchCode;
    private String productionArea;
    private LocalDate plantDate;
    private LocalDate harvestDate;
    private Map<String, Object> metadata;
}
