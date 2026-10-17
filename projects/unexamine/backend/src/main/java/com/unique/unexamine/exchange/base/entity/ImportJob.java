package com.unique.unexamine.exchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块 Excel 导入的预检查、确认和错误行历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_exchange_import_job")
public class ImportJob {

    /**
     * 模块数据导入任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 导入任务所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 导入任务所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 目标业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 预检查时的模块发布版本主键
     */
    @TableField("module_version_id")
    private Long moduleVersionId;

    /**
     * 提交导入的平台账号主键
     */
    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    /**
     * 只新增或按记录编号匹配更新的导入模式
     */
    @TableField("`mode`")
    private String mode;

    /**
     * 本地存储中随机生成的源 Excel 文件名
     */
    @TableField("source_key")
    private String sourceKey;

    /**
     * 用户上传的原始 Excel 文件名
     */
    @TableField("source_name")
    private String sourceName;

    /**
     * 预检查文件内容摘要
     */
    @TableField("source_sha256")
    private String sourceSha256;

    /**
     * 逐行预检查结果和错误原因快照
     */
    @TableField("preview_json")
    private String previewJson;

    /**
     * 已预检查、含错误、已导入或失败状态
     */
    @TableField("`status`")
    private String status;

    /**
     * Excel 中待导入的数据行数
     */
    @TableField("total_rows")
    private Integer totalRows;

    /**
     * 预检查通过的数据行数
     */
    @TableField("valid_rows")
    private Integer validRows;

    /**
     * 确认后成功落库的数据行数
     */
    @TableField("imported_rows")
    private Integer importedRows;

    /**
     * 确认失败时显示给提交人的原因
     */
    @TableField("failure_reason")
    private String failureReason;

    /**
     * 文件上传和预检查时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 确认导入或失败时间
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

    public Long getModuleVersionId() {
        return moduleVersionId;
    }

    public void setModuleVersionId(Long moduleVersionId) {
        this.moduleVersionId = moduleVersionId;
    }

    public Long getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Long requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceSha256() {
        return sourceSha256;
    }

    public void setSourceSha256(String sourceSha256) {
        this.sourceSha256 = sourceSha256;
    }

    public String getPreviewJson() {
        return previewJson;
    }

    public void setPreviewJson(String previewJson) {
        this.previewJson = previewJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getValidRows() {
        return validRows;
    }

    public void setValidRows(Integer validRows) {
        this.validRows = validRows;
    }

    public Integer getImportedRows() {
        return importedRows;
    }

    public void setImportedRows(Integer importedRows) {
        this.importedRows = importedRows;
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

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    @Override
    public String toString() {
        return "ImportJob{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", moduleVersionId = " + moduleVersionId +
            ", requestedByAccountId = " + requestedByAccountId +
            ", mode = " + mode +
            ", sourceKey = " + sourceKey +
            ", sourceName = " + sourceName +
            ", sourceSha256 = " + sourceSha256 +
            ", previewJson = " + previewJson +
            ", status = " + status +
            ", totalRows = " + totalRows +
            ", validRows = " + validRows +
            ", importedRows = " + importedRows +
            ", failureReason = " + failureReason +
            ", createTime = " + createTime +
            ", finishTime = " + finishTime +
            "}";
    }
}
