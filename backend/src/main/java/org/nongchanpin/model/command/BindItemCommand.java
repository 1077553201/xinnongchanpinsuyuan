package org.nongchanpin.model.command;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindItemCommand {
    @NotBlank
    private String itemCode;

    @NotBlank
    private String batchCode;

    private Object payload;
}
