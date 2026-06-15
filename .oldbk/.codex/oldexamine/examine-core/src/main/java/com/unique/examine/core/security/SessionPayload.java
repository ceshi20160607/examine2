package com.unique.examine.core.security;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record SessionPayload(
        @JsonSerialize(using = ToStringSerializer.class) Long platId,
        String username,
        @JsonSerialize(using = ToStringSerializer.class) Long systemId,
        @JsonSerialize(using = ToStringSerializer.class) Long tenantId) {
}
