package org.nongchanpin.model.dto;

import java.time.LocalDateTime;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class CreateTraceEventRequest {
    @NotBlank
    @Pattern(regexp = "SOWING|FARMING|HARVEST|PROCESSING|QUALITY_TEST|WAREHOUSE_IN|WAREHOUSE_OUT|LOGISTICS|STORE_ARRIVAL|SALE|RECALL")
    private String eventType;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;
    @Size(max = 255)
    private String location;
    @Size(max = 128)
    private String operatorName;
    private Map<String, Object> payload;
}
