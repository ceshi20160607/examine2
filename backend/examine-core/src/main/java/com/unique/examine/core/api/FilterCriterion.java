package com.unique.examine.core.api;

/**
 * Single filter criterion.
 */
public record FilterCriterion(String field, FilterOperator operator, Object value) {
}

