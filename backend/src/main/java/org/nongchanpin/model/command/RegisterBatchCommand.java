package org.nongchanpin.model.command;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterBatchCommand {
    @NotBlank
    private String batchCode;

    @NotBlank
    private String productCode;

    private Object payload;

    private Long startedAt;
}
