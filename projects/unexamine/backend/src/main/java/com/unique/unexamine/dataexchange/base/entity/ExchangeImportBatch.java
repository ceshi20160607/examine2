package com.unique.unexamine.dataexchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 固定模块、映射和权限版本的导入预检与执行批次
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_exchange_import_batch")
public class ExchangeImportBatch {

    /**
     * 导入批次主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 执行系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 执行租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 导入目标模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 导入预检时固定的模块配置版本
     */
    @TableField("module_config_version_id")
    private Long moduleConfigVersionId;

    /**
     * 导入字段映射主键
     */
    @TableField("mapping_id")
    private Long mappingId;

    /**
     * 导入预检时固定的映射发布版本
     */
    @TableField("mapping_version_id")
    private Long mappingVersionId;

    /**
     * 导入预检时固定的权限版本
     */
    @TableField("permission_version_id")
    private Long permissionVersionId;

    /**
     * 原始导入文件主键
     */
    @TableField("source_file_id")
    private Long sourceFileId;

    /**
     * 预检或执行失败行结果文件主键
     */
    @TableField("error_file_id")
    private Long errorFileId;

    /**
     * 异步作业业务申请主键，运行状态存 Redis
     */
    @TableField("job_id")
    private Long jobId;

    /**
     * 新增、更新或混合导入模式
     */
    @TableField("`mode`")
    private String mode;

    /**
     * 预检、执行和回滚业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 原始数据行数
     */
    @TableField("total_rows")
    private Long totalRows;

    /**
     * 预检通过行数
     */
    @TableField("valid_rows")
    private Long validRows;

    /**
     * 成功写入行数
     */
    @TableField("success_rows")
    private Long successRows;

    /**
     * 失败行数
     */
    @TableField("failed_rows")
    private Long failedRows;

    /**
     * 预检时固定的权限、字段和数据范围快照
     */
    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    /**
     * 预检、执行和回滚的结果摘要
     */
    @TableField("summary_json")
    private String summaryJson;

    /**
     * 发起导入的系统成员主键
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 开始执行时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 完成或回滚时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 导入批次乐观锁版本
     */
    @Version
    @TableField("version")
    private Integer version;

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

    public Long getModuleConfigVersionId() {
        return moduleConfigVersionId;
    }

    public void setModuleConfigVersionId(Long moduleConfigVersionId) {
        this.moduleConfigVersionId = moduleConfigVersionId;
    }

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public Long getMappingVersionId() {
        return mappingVersionId;
    }

    public void setMappingVersionId(Long mappingVersionId) {
        this.mappingVersionId = mappingVersionId;
    }

    public Long getPermissionVersionId() {
        return permissionVersionId;
    }

    public void setPermissionVersionId(Long permissionVersionId) {
        this.permissionVersionId = permissionVersionId;
    }

    public Long getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(Long sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public Long getErrorFileId() {
        return errorFileId;
    }

    public void setErrorFileId(Long errorFileId) {
        this.errorFileId = errorFileId;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Long totalRows) {
        this.totalRows = totalRows;
    }

    public Long getValidRows() {
        return validRows;
    }

    public void setValidRows(Long validRows) {
        this.validRows = validRows;
    }

    public Long getSuccessRows() {
        return successRows;
    }

    public void setSuccessRows(Long successRows) {
        this.successRows = successRows;
    }

    public Long getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(Long failedRows) {
        this.failedRows = failedRows;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ExchangeImportBatch{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", moduleConfigVersionId = " + moduleConfigVersionId +
            ", mappingId = " + mappingId +
            ", mappingVersionId = " + mappingVersionId +
            ", permissionVersionId = " + permissionVersionId +
            ", sourceFileId = " + sourceFileId +
            ", errorFileId = " + errorFileId +
            ", jobId = " + jobId +
            ", mode = " + mode +
            ", status = " + status +
            ", totalRows = " + totalRows +
            ", validRows = " + validRows +
            ", successRows = " + successRows +
            ", failedRows = " + failedRows +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", summaryJson = " + summaryJson +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", version = " + version +
            "}";
    }
}
