package org.nongchanpin.model.dto;

import java.time.LocalDate;
import javax.validation.constraints.*;
import lombok.Data;

@Data
public class EndPlantingRequest {
    @NotNull @PastOrPresent
    private LocalDate endedOn;
    @NotBlank @Size(max = 1000)
    private String reason;
}
