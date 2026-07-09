package com.unique.unexamine.core.admin;

public record AdminConfigUpdateRequest(
        String description,
        String status
) {
}