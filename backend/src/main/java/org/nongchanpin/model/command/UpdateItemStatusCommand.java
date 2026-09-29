package org.nongchanpin.model.command;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateItemStatusCommand {
    @NotBlank
    private String itemCode;

    @NotNull
    private Integer status;

    private Object reasonPayload;
}
