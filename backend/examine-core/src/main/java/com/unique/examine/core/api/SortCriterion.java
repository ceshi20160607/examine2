package com.unique.examine.core.api;

/**
 * Single sort criterion.
 */
public record SortCriterion(String field, SortDirection direction) {
}

