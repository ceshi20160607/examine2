package com.unique.examine.module.manage.service;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

final class ConfigErrors {
    private ConfigErrors() {
    }

    static BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    static BusinessException invalidReference(String message) {
        return new BusinessException("CONFIG_REFERENCE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    static BusinessException notFound() {
        return new BusinessException("RESOURCE_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND);
    }

    static BusinessException versionConflict() {
        return new BusinessException("CONFIG_VERSION_CONFLICT", "草稿或资源版本已变化，请刷新后重试", HttpStatus.CONFLICT);
    }

    static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    static long id(String value, String field) {
        try {
            var id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw invalid(field + " 格式无效");
        }
    }

    static Long nullableId(String value, String field) {
        return value == null || value.isBlank() ? null : id(value, field);
    }

    static long version(String value) {
        try {
            var version = Long.parseLong(value);
            if (version < 0) {
                throw new NumberFormatException();
            }
            return version;
        } catch (NumberFormatException exception) {
            throw invalid("version 格式无效");
        }
    }

    static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
