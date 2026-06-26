package com.unique.examine.core.api;

/**
 * Field-level validation error returned to the frontend.
 */
public record ErrorField(String field, String message, String code) {
}

