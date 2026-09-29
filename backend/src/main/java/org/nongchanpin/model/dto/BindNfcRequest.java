package org.nongchanpin.model.dto;

import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BindNfcRequest {
    @NotNull
    private Long itemId;
    @NotBlank
    private String nfcUid;
    private String tagHash;
    private Map<String, Object> payload;
}
