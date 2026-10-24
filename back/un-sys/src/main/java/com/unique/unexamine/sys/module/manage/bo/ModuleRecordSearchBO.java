package com.unique.unexamine.sys.module.manage.bo;

import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;
import java.util.List;

/** 模块记录的范围、场景、字段条件和展示列查询。 */
public class ModuleRecordSearchBO extends SearchBO {
    private String scope = "ALL";
    private Long sceneId;
    private List<ModuleSceneDto.Condition> filters;
    private Long sortFieldId;
    private Boolean descending;
    private List<Long> columnFieldIds;

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getSceneId() { return sceneId; }
    public void setSceneId(Long sceneId) { this.sceneId = sceneId; }
    public List<ModuleSceneDto.Condition> getFilters() { return filters; }
    public void setFilters(List<ModuleSceneDto.Condition> filters) { this.filters = filters; }
    public Long getSortFieldId() { return sortFieldId; }
    public void setSortFieldId(Long sortFieldId) { this.sortFieldId = sortFieldId; }
    public Boolean getDescending() { return descending; }
    public void setDescending(Boolean descending) { this.descending = descending; }
    public List<Long> getColumnFieldIds() { return columnFieldIds; }
    public void setColumnFieldIds(List<Long> columnFieldIds) { this.columnFieldIds = columnFieldIds; }
}
