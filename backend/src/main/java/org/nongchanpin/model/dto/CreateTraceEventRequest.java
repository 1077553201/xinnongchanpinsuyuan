package org.nongchanpin.model.dto;

import java.time.LocalDateTime;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTraceEventRequest {
    @NotBlank
    private String eventType;
    @NotNull
    private LocalDateTime eventTime;
    private String location;
    private String operatorName;
    private Map<String, Object> payload;
}
