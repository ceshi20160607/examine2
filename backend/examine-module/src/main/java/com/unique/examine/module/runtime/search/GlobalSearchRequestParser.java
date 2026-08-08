package com.unique.examine.module.runtime.search;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.query.RecordSearchTokenizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GlobalSearchRequestParser {
    public SearchRequest parse(String q, String page, String size) {
        var normalized = RecordSearchTokenizer.normalize(q);
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw invalid("q must contain 2..100 normalized characters");
        }
        try {
            RecordSearchTokenizer.queryTokens(normalized);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        return new SearchRequest(normalized, integer(page, "page", 1, 1_000_000),
                integer(size, "size", 1, 50));
    }

    private static int integer(String value, String name, int minimum, int maximum) {
        if (value == null || !value.matches("^[0-9]{1,10}$")) {
            throw invalid(name + " must be an integer");
        }
        try {
            var parsed = Long.parseLong(value);
            if (parsed < minimum || parsed > maximum) {
                throw invalid(name + " is outside its supported range");
            }
            return (int) parsed;
        } catch (NumberFormatException exception) {
            throw invalid(name + " must be an integer");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("GLOBAL_SEARCH_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record SearchRequest(String q, int page, int size) {
    }
}
