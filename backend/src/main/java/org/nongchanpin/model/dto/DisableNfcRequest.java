package org.nongchanpin.model.dto;

import java.util.Map;
import lombok.Data;

@Data
public class DisableNfcRequest {
    private Map<String, Object> reasonPayload;
}
