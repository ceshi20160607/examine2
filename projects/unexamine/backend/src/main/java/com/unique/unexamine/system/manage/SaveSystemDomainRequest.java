package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveSystemDomainRequest(
        Long id,
        @NotBlank @Pattern(regexp = "SUBDOMAIN|CUSTOM") String domainType,
        @NotBlank @Size(max = 255) String host,
        @NotBlank @Size(max = 255) String basePath,
        @NotNull Boolean tlsRequired,
        Integer expectedVersion) {
}
