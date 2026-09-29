package org.nongchanpin.model.command;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindNfcTagCommand {
    @NotBlank
    private String itemCode;

    @NotBlank
    private String nfcUid;

    private Object payload;
}
