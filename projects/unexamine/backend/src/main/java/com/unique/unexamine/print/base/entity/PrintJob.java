package com.unique.unexamine.print.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 绑定同模块模板版本与记录的打印结果审计
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_print_job")
public class PrintJob {

    /**
     * 打印预览或输出作业主键
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
     * 目标业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 打印目标业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 生成快照时目标记录乐观锁版本
     */
    @TableField("record_version")
    private Integer recordVersion;

    /**
     * 固定使用的不可变打印模板版本
     */
    @TableField("template_version_id")
    private Long templateVersionId;

    /**
     * 模板版本固定的模块配置版本
     */
    @TableField("module_config_version_id")
    private Long moduleConfigVersionId;

    /**
     * 打印时固定的权限版本
     */
    @TableField("permission_version_id")
    private Long permissionVersionId;

    /**
     * 异步作业业务申请主键，运行状态存 Redis
     */
    @TableField("background_job_id")
    private Long backgroundJobId;

    /**
     * 打印时字段、附件、签章与数据范围权限快照
     */
    @TableField("authorization_snapshot_json")
    private String authorizationSnapshotJson;

    /**
     * 打印时目标记录及关联字段展示值快照
     */
    @TableField("record_snapshot_json")
    private String recordSnapshotJson;

    /**
     * 生成中、成功或失败状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 生成的 PDF 文件主键
     */
    @TableField("output_file_id")
    private Long outputFileId;

    /**
     * 失败原因摘要
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 发起打印的系统成员主键
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 生成完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 打印作业乐观锁版本
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

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Integer getRecordVersion() {
        return recordVersion;
    }

    public void setRecordVersion(Integer recordVersion) {
        this.recordVersion = recordVersion;
    }

    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long templateVersionId) {
        this.templateVersionId = templateVersionId;
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

    public Long getBackgroundJobId() {
        return backgroundJobId;
    }

    public void setBackgroundJobId(Long backgroundJobId) {
        this.backgroundJobId = backgroundJobId;
    }

    public String getAuthorizationSnapshotJson() {
        return authorizationSnapshotJson;
    }

    public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) {
        this.authorizationSnapshotJson = authorizationSnapshotJson;
    }

    public String getRecordSnapshotJson() {
        return recordSnapshotJson;
    }

    public void setRecordSnapshotJson(String recordSnapshotJson) {
        this.recordSnapshotJson = recordSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOutputFileId() {
        return outputFileId;
    }

    public void setOutputFileId(Long outputFileId) {
        this.outputFileId = outputFileId;
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
        return "PrintJob{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", recordVersion = " + recordVersion +
            ", templateVersionId = " + templateVersionId +
            ", moduleConfigVersionId = " + moduleConfigVersionId +
            ", permissionVersionId = " + permissionVersionId +
            ", backgroundJobId = " + backgroundJobId +
            ", authorizationSnapshotJson = " + authorizationSnapshotJson +
            ", recordSnapshotJson = " + recordSnapshotJson +
            ", status = " + status +
            ", outputFileId = " + outputFileId +
            ", errorMessage = " + errorMessage +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", finishTime = " + finishTime +
            ", version = " + version +
            "}";
    }
}
