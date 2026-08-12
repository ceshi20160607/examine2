package com.unique.examine.module.report.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ReportRequests {
    private ReportRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$")
            String dataSourceId,
            @NotNull @Size(max = 100)
            List<@NotBlank @Size(max = 64) String> outputFieldCodes
    ) {
        public Create {
            outputFieldCodes = outputFieldCodes == null
                    ? null : List.copyOf(outputFieldCodes);
        }
    }

    public record SaveDraft(
            @NotNull Long expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2_000) String description,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$")
            String dataSourceId,
            @NotNull @Size(max = 100)
            List<@NotBlank @Size(max = 64) String> outputFieldCodes
    ) {
        public SaveDraft {
            outputFieldCodes = outputFieldCodes == null
                    ? null : List.copyOf(outputFieldCodes);
        }
    }

    public record Publish(@NotNull Long expectedVersion) {
    }
}
