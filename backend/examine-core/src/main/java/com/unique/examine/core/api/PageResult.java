package com.unique.examine.core.api;

import java.util.List;

/**
 * Unified page result for list APIs.
 *
 * @param records page records
 * @param pageNo current page
 * @param pageSize page size
 * @param total total rows
 * @param hasNext whether another page exists
 * @param <T> row type
 */
public record PageResult<T>(List<T> records, int pageNo, int pageSize, long total, boolean hasNext) {
}

