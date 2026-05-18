package com.unique.examine.module.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ModuleRecordDslQuery {

    /** 由后端会话强制注入 */
    private Long systemId;
    /** 由后端会话强制注入 */
    private Long tenantId;

    private Long appId;
    private Long modelId;

    private Long page = 1L;
    private Long limit = 20L;

    /** 排序键（白名单）：updateTime/createTime/id */
    private String sortBy = "updateTime";
    /** asc/desc */
    private String sortDir = "desc";

    private List<ModuleRecordDslFilter> filters = new ArrayList<>();

    /**
     * 非系统所有者查询时由后端注入：仅可见本人创建的记录（create_user_id）。
     * @deprecated 使用 scopeCreateUserIds
     */
    private Long scopeCreateUserId;

    /** 数据权限：可见记录的 create_user_id 白名单（空且 unrestricted 时不加条件） */
    private List<Long> scopeCreateUserIds;

    /** true 时不按创建人过滤（系统所有者或角色 data_scope=全部） */
    private Boolean scopeUnrestricted;

    /**
     * 非空时：在 list 每条记录上附带 {@code data}（field_code → value_text），一次批量查询 EAV，避免 N+1 detail。
     */
    private List<String> includeFieldCodes = new ArrayList<>();
}

