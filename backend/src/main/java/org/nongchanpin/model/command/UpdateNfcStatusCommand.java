package org.nongchanpin.model.command;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateNfcStatusCommand {
    @NotBlank
    private String nfcUid;

    @NotNull
    private Integer status;

    private Object reasonPayload;
}
