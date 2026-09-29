package org.nongchanpin.model.command;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppendSensorWindowCommand {
    @NotBlank
    private String batchCode;

    @NotBlank
    private String windowId;

    @NotBlank
    private String deviceCode;

    private Object payload;

    private Long windowStart;

    private Long windowEnd;
}
