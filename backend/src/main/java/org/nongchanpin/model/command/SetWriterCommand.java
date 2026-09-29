package org.nongchanpin.model.command;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetWriterCommand {
    @NotBlank
    private String writerAddress;

    @NotNull
    private Boolean allowed;
}
