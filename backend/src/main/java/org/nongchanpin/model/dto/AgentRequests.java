package org.nongchanpin.model.dto;

import java.time.LocalDate;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.Data;
import org.nongchanpin.model.entity.AgentModels.Operation;

public final class AgentRequests {
    private AgentRequests() {}
    @Data public static class Create {
        @NotBlank @Size(max=12000) private String sourceText;
        @NotNull private LocalDate reportDate;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
    @Data public static class Revise {
        @NotNull @Min(1) private Integer version;
        @NotNull @Size(min=1,max=20) @Valid private List<@NotNull Operation> operations;
    }
    @Data public static class Confirm {
        @NotNull @Min(1) private Integer version;
        @NotNull @Size(min=1,max=20) private List<@NotBlank String> operationIds;
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{36}") private String requestKey;
    }
}
