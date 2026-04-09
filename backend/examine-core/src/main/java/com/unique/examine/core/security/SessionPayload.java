package com.unique.examine.core.security;

public record SessionPayload(Long platId, String username, Long systemId, Long tenantId) {
}
