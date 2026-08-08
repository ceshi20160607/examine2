package com.unique.examine.core.api;

public record ApiError(String code, String path, String message) {
}
