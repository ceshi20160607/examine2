package com.unique.examine.module.runtime.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecordMyDraftsQueryPlanTest {
    @Test
    void fixesDraftOwnerCurrentRuntimeIdentityScopeAndStableOrder() {
        var plan = RecordMyDraftsQueryPlan.from(request(2, 50, null));
        var where = plan.whereSql("(r.owner_department_id IN (?,?))");

        assertThat(where).isEqualTo(
                "r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? "
                        + "AND r.schema_version_id=? AND r.module_snapshot_id=? "
                        + "AND r.status='DRAFT' AND r.owner_member_id=? "
                        + "AND (r.owner_department_id IN (?,?))");
        assertThat(where).doesNotContain("ACTIVE", "ARCHIVED");
        assertThat(RecordMyDraftsQueryPlan.ORDER_BY)
                .isEqualTo("r.updated_at DESC,r.record_id ASC");
        assertThat(plan.offset()).isEqualTo(50);
    }

    @Test
    void normalizesQueryAndSearchesOnlyRecordNumberAndTitleWithEscapedLikePattern() {
        var plan = RecordMyDraftsQueryPlan.from(request(1, 20, "　ＡＬＰＨＡ\t ５０%_!　"));
        var where = plan.whereSql("1=1");

        assertThat(plan.query()).isEqualTo("alpha 50%_!");
        assertThat(plan.likePattern()).isEqualTo("%alpha 50!%!_!!%");
        assertThat(where).endsWith(
                "AND 1=1 AND (r.record_no LIKE ? ESCAPE '!' OR r.title LIKE ? ESCAPE '!')");
        assertThat(where).doesNotContain("string_value", "json_value", "field_code");
    }

    @Test
    void acceptsFrozenBoundsAndRejectsMissingOrInvalidInputsWithDedicated422Code() {
        assertThat(RecordMyDraftsQueryPlan.from(request(1, 1, "草稿")).offset()).isZero();
        assertThat(RecordMyDraftsQueryPlan.from(request(1_000_000, 200, null)).size())
                .isEqualTo(200);

        assertInvalid(null);
        assertInvalid(request(0, 50, null));
        assertInvalid(request(1_000_001, 50, null));
        assertInvalid(request(1, 0, null));
        assertInvalid(request(1, 201, null));
        assertInvalid(request(1, 50, " "));
        assertInvalid(request(1, 50, "x"));
        assertInvalid(request(1, 50, "x".repeat(101)));
    }

    private static void assertInvalid(RecordRuntimeViews.MyDraftsQueryRequest request) {
        var exception = catchThrowableOfType(
                () -> RecordMyDraftsQueryPlan.from(request),
                BusinessException.class);
        assertThat(exception.code()).isEqualTo("MY_DRAFTS_QUERY_INVALID");
        assertThat(exception.status().value()).isEqualTo(422);
    }

    private static RecordRuntimeViews.MyDraftsQueryRequest request(
            int page,
            int size,
            String query
    ) {
        return new RecordRuntimeViews.MyDraftsQueryRequest(page, size, query);
    }
}
