package com.unique.examine.module.runtime.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.query.RecordSearchTokenizer;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

final class RecordMyDraftsQueryPlan {
    static final String ORDER_BY = "r.updated_at DESC,r.record_id ASC";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private final int page;
    private final int size;
    private final String query;

    private RecordMyDraftsQueryPlan(int page, int size, String query) {
        this.page = page;
        this.size = size;
        this.query = query;
    }

    static RecordMyDraftsQueryPlan from(RecordRuntimeViews.MyDraftsQueryRequest request) {
        if (request == null || request.page() < 1 || request.page() > 1_000_000
                || request.size() < 1 || request.size() > 200) {
            throw invalid("分页参数无效");
        }
        var query = normalize(request.q());
        if (query != null) {
            var length = query.codePointCount(0, query.length());
            if (length < 2 || length > 100) {
                throw invalid("查询关键词长度必须为 2 到 100 个字符");
            }
        }
        return new RecordMyDraftsQueryPlan(request.page(), request.size(), query);
    }

    int page() {
        return page;
    }

    int size() {
        return size;
    }

    long offset() {
        return Math.multiplyExact((long) page - 1, size);
    }

    String query() {
        return query;
    }

    String likePattern() {
        if (query == null) {
            return null;
        }
        return "%" + query
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    String whereSql(String scopeSql) {
        var where = "r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? "
                + "AND r.schema_version_id=? AND r.module_snapshot_id=? "
                + "AND r.status='DRAFT' AND r.owner_member_id=? AND " + scopeSql;
        if (query != null) {
            where += " AND (r.record_no LIKE ? ESCAPE '!' OR r.title LIKE ? ESCAPE '!')";
        }
        return where;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return WHITESPACE.matcher(RecordSearchTokenizer.normalize(value).strip()).replaceAll(" ");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("MY_DRAFTS_QUERY_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
