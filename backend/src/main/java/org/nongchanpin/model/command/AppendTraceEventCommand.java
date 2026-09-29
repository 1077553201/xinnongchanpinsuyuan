package org.nongchanpin.model.command;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppendTraceEventCommand {
    @NotBlank
    private String itemCode;

    @NotBlank
    private String eventId;

    @NotNull
    private Integer eventType;

    private Object payload;

    private Long occurredAt;
}
