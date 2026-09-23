package com.unique.unexamine.runtimedata.manage;

import java.time.LocalDateTime;
import java.util.List;

/** 个人列表视图；查询范围与条件一同恢复，不能因重新进入而扩大可见数据。 */
public record RuntimeListView(
        Long id,
        String code,
        String name,
        String search,
        List<RuntimeListFilter> filters,
        String sortField,
        String sortDirection,
        List<String> visibleFieldCodes,
        List<String> fixedFieldCodes,
        Integer pageSize,
        boolean defaultView,
        Integer version,
        LocalDateTime updatedAt,
        boolean queryInvalid,
        /** 历史 JSON 未保存范围时，兼容原有 ACTIVE 行为。 */
        String lifecycleState,
        /** 历史 JSON 未保存租户范围时，兼容原有 ALL 行为。 */
        String tenantScope) {
}
