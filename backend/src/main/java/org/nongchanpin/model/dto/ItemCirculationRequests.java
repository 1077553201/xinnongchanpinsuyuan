package org.nongchanpin.model.dto;

import java.util.List;
import javax.validation.constraints.*;
import lombok.Data;

public final class ItemCirculationRequests {
    private ItemCirculationRequests() {}
    @Data public static class Link {
        @NotEmpty @Size(max=100) private List<@NotNull @Positive Long> itemIds;
        @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") private String requestKey;
    }
    @Data public static class Receipt {
        @NotEmpty @Size(max=100) private List<@NotNull @Positive Long> itemIds;
        @NotBlank @Pattern(regexp="ACCEPTED|REJECTED") private String outcome;
        @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") private String requestKey;
    }
}
