package org.nongchanpin.model.dto;

import java.time.LocalDate;
import javax.validation.constraints.*;
import lombok.Data;

@Data
public class StartPlantingRequest {
    @NotNull @Positive
    private Long productId;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    private String cycleCode;
    @NotBlank @Size(max = 128)
    private String variety;
    @NotNull @PastOrPresent
    private LocalDate plantedOn;
    private LocalDate expectedHarvestOn;
    @NotBlank @Size(max = 64)
    private String managerName;
    @Size(max = 2000)
    private String environmentNotes;
}
