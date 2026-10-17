package com.unique.unexamine.dataexchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 固定数据范围、字段、权限和结果文件的异步导出批次
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_exchange_export_batch")
public class ExchangeExportBatch {

    /**
     * 导出批次主键
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
     * 导出来源模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 导出提交时固定的模块配置版本
     */
    @TableField("module_config_version_id")
    private Long moduleConfigVersionId;

    /**
     * 导出提交时固定的权限版本
     */
    @TableField("permission_version_id")
    private Long permissionVersionId;

    /**
     * 采用已发布导出映射时的映射主键
     */
    @TableField("mapping_id")
    private Long mappingId;

    /**
     * 采用已发布导出映射时固定的版本主键
     */
    @TableField("mapping_version_id")
    private Long mappingVersionId;

    /**
     * 异步作业业务申请主键，运行状态存 Redis
     */
    @TableField("job_id")
    private Long jobId;

    /**
     * 结果格式，如 CSV、XLSX 或 PDF
     */
    @TableField("export_format")
    private String exportFormat;

    /**
     * 提交时固定的筛选条件和数据范围
     */
    @TableField("filter_snapshot_json")
    private String filterSnapshotJson;

    /**
     * 提交时固定的权限与字段脱敏规则
     */
    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    /**
     * 导出字段及列顺序快照
     */
    @TableField("selected_fields_json")
    private String selectedFieldsJson;

    /**
     * 导出业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 导出范围内总行数
     */
    @TableField("total_rows")
    private Long totalRows;

    /**
     * 已成功写入结果的行数
     */
    @TableField("exported_rows")
    private Long exportedRows;

    /**
     * 成功导出的结果文件主键
     */
    @TableField("result_file_id")
    private Long resultFileId;

    /**
     * 导出失败原因摘要
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 发起导出的系统成员主键
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
     * 执行完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 导出批次乐观锁版本
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

    public Long getPermissionVersionId() {
        return permissionVersionId;
    }

    public void setPermissionVersionId(Long permissionVersionId) {
        this.permissionVersionId = permissionVersionId;
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

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getExportFormat() {
        return exportFormat;
    }

    public void setExportFormat(String exportFormat) {
        this.exportFormat = exportFormat;
    }

    public String getFilterSnapshotJson() {
        return filterSnapshotJson;
    }

    public void setFilterSnapshotJson(String filterSnapshotJson) {
        this.filterSnapshotJson = filterSnapshotJson;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getSelectedFieldsJson() {
        return selectedFieldsJson;
    }

    public void setSelectedFieldsJson(String selectedFieldsJson) {
        this.selectedFieldsJson = selectedFieldsJson;
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

    public Long getExportedRows() {
        return exportedRows;
    }

    public void setExportedRows(Long exportedRows) {
        this.exportedRows = exportedRows;
    }

    public Long getResultFileId() {
        return resultFileId;
    }

    public void setResultFileId(Long resultFileId) {
        this.resultFileId = resultFileId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
        return "ExchangeExportBatch{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", moduleConfigVersionId = " + moduleConfigVersionId +
            ", permissionVersionId = " + permissionVersionId +
            ", mappingId = " + mappingId +
            ", mappingVersionId = " + mappingVersionId +
            ", jobId = " + jobId +
            ", exportFormat = " + exportFormat +
            ", filterSnapshotJson = " + filterSnapshotJson +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", selectedFieldsJson = " + selectedFieldsJson +
            ", status = " + status +
            ", totalRows = " + totalRows +
            ", exportedRows = " + exportedRows +
            ", resultFileId = " + resultFileId +
            ", errorMessage = " + errorMessage +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", version = " + version +
            "}";
    }
}
