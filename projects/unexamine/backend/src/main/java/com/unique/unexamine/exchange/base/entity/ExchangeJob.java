package com.unique.unexamine.exchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块筛选数据的异步导出任务
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_exchange_job")
public class ExchangeJob {

    /**
     * 模块数据导出任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 任务所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 任务导出的业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 提交任务的平台账号主键
     */
    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    /**
     * 提交时的标题和字段筛选条件快照
     */
    @TableField("filter_json")
    private String filterJson;

    /**
     * 提交时选择的模块字段编码快照
     */
    @TableField("columns_json")
    private String columnsJson;

    /**
     * 排队、运行、成功或失败状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 成功结果在本地存储中的随机文件名
     */
    @TableField("result_key")
    private String resultKey;

    /**
     * 导出成功的数据行数
     */
    @TableField("`row_count`")
    private Integer rowCount;

    /**
     * 失败时给请求者展示的原因
     */
    @TableField("failure_reason")
    private String failureReason;

    /**
     * 任务提交时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 任务开始处理时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 任务结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Long requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
    }

    public String getFilterJson() {
        return filterJson;
    }

    public void setFilterJson(String filterJson) {
        this.filterJson = filterJson;
    }

    public String getColumnsJson() {
        return columnsJson;
    }

    public void setColumnsJson(String columnsJson) {
        this.columnsJson = columnsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultKey() {
        return resultKey;
    }

    public void setResultKey(String resultKey) {
        this.resultKey = resultKey;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    @Override
    public String toString() {
        return "ExchangeJob{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", requestedByAccountId = " + requestedByAccountId +
            ", filterJson = " + filterJson +
            ", columnsJson = " + columnsJson +
            ", status = " + status +
            ", resultKey = " + resultKey +
            ", rowCount = " + rowCount +
            ", failureReason = " + failureReason +
            ", createTime = " + createTime +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            "}";
    }
}
