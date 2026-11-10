package com.unique.unexamine.sys.module.manage.bo;

import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

/** 模块记录的范围、场景、字段条件和展示列查询。 */
public class ModuleRecordSearchBO extends SearchBO {
    private String scope = "ALL";
    private Long sceneId;
    private List<ModuleSceneDto.Condition> filters;
    private Long sortFieldId;
    private Boolean descending;
    private List<Long> columnFieldIds;
    /** 关联数据源按已知业务主键回查时使用；仍由模块列表统一执行权限和逻辑删除过滤。 */
    private List<Long> recordIds;
    /** 数据源固定关键词与调用方关键词同时存在时，第二个词也必须匹配。 */
    private String additionalKeyword;
    /** 业务状态和当前审批状态在主表过滤，均须先通过字段读取授权。 */
    @Schema(description="模块业务状态编码")
    private String statusCode;
    @Schema(description="当前审批状态：0 未提交，1 审批中，2 通过，3 拒绝，4 取消")
    private Integer checkStatus;
    /** 数据源固定场景与调用方场景同时存在时，第二个场景也必须匹配。 */
    private Long additionalSceneId;
    /** 按模块页面状态字段编码筛选 examine 的当前审批状态。 */
    @Schema(description="审批状态筛选：状态组编码到RUNNING、COMPLETED、REJECTED或CANCELLED")
    private Map<String,String> approvalStatusFilters;

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
    public List<Long> getRecordIds() { return recordIds; }
    public void setRecordIds(List<Long> recordIds) { this.recordIds = recordIds; }
    public String getAdditionalKeyword() { return additionalKeyword; }
    public void setAdditionalKeyword(String additionalKeyword) { this.additionalKeyword = additionalKeyword; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode=statusCode; }
    public Integer getCheckStatus() { return checkStatus; }
    public void setCheckStatus(Integer checkStatus) { this.checkStatus=checkStatus; }
    public Long getAdditionalSceneId() { return additionalSceneId; }
    public void setAdditionalSceneId(Long additionalSceneId) { this.additionalSceneId = additionalSceneId; }
    public Map<String,String> getApprovalStatusFilters() { return approvalStatusFilters; }
    public void setApprovalStatusFilters(Map<String,String> approvalStatusFilters) { this.approvalStatusFilters=approvalStatusFilters; }
}
