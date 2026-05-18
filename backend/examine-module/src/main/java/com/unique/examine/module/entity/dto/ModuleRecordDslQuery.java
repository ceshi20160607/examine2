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
     */
    private Long scopeCreateUserId;
}

