package com.unique.unexamine.dataexchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 导入逐行操作、前镜像及版本化回滚依据
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_exchange_import_row")
public class ExchangeImportRow {

    /**
     * 导入原始行处理结果主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属导入批次主键
     */
    @TableField("batch_id")
    private Long batchId;

    /**
     * 批次执行租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 批次目标模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 原文件中的行号
     */
    @TableField("`row_number`")
    private Long rowNumber;

    /**
     * 原文件行内容快照
     */
    @TableField("raw_json")
    private String rawJson;

    /**
     * 映射及预检后的字段值快照
     */
    @TableField("normalized_json")
    private String normalizedJson;

    /**
     * 预检、执行或回滚的行级状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 预检确定的 CREATE、UPDATE 或 SKIP 操作
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * 命中或写入的业务记录主键
     */
    @TableField("target_record_id")
    private Long targetRecordId;

    /**
     * UPDATE 执行前预期的目标记录乐观锁版本
     */
    @TableField("expected_record_version")
    private Integer expectedRecordVersion;

    /**
     * UPDATE 前完整业务数据快照，仅用于回滚本批修改
     */
    @TableField("before_snapshot_json")
    private String beforeSnapshotJson;

    /**
     * 本批写入后得到的业务记录乐观锁版本
     */
    @TableField("applied_record_version")
    private Integer appliedRecordVersion;

    /**
     * 本批回滚成功后业务记录的乐观锁版本
     */
    @TableField("rollback_record_version")
    private Integer rollbackRecordVersion;

    /**
     * 本批回滚结果，成功或后续修改冲突
     */
    @TableField("rollback_status")
    private String rollbackStatus;

    /**
     * 本批行级回滚完成或冲突确认时间
     */
    @TableField("rollback_time")
    private LocalDateTime rollbackTime;

    /**
     * 预检、写入或回滚失败原因
     */
    @TableField("error_json")
    private String errorJson;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 行级结果乐观锁版本
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

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
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

    public Long getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Long rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public String getNormalizedJson() {
        return normalizedJson;
    }

    public void setNormalizedJson(String normalizedJson) {
        this.normalizedJson = normalizedJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public Integer getExpectedRecordVersion() {
        return expectedRecordVersion;
    }

    public void setExpectedRecordVersion(Integer expectedRecordVersion) {
        this.expectedRecordVersion = expectedRecordVersion;
    }

    public String getBeforeSnapshotJson() {
        return beforeSnapshotJson;
    }

    public void setBeforeSnapshotJson(String beforeSnapshotJson) {
        this.beforeSnapshotJson = beforeSnapshotJson;
    }

    public Integer getAppliedRecordVersion() {
        return appliedRecordVersion;
    }

    public void setAppliedRecordVersion(Integer appliedRecordVersion) {
        this.appliedRecordVersion = appliedRecordVersion;
    }

    public Integer getRollbackRecordVersion() {
        return rollbackRecordVersion;
    }

    public void setRollbackRecordVersion(Integer rollbackRecordVersion) {
        this.rollbackRecordVersion = rollbackRecordVersion;
    }

    public String getRollbackStatus() {
        return rollbackStatus;
    }

    public void setRollbackStatus(String rollbackStatus) {
        this.rollbackStatus = rollbackStatus;
    }

    public LocalDateTime getRollbackTime() {
        return rollbackTime;
    }

    public void setRollbackTime(LocalDateTime rollbackTime) {
        this.rollbackTime = rollbackTime;
    }

    public String getErrorJson() {
        return errorJson;
    }

    public void setErrorJson(String errorJson) {
        this.errorJson = errorJson;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ExchangeImportRow{" +
            "id = " + id +
            ", batchId = " + batchId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", rowNumber = " + rowNumber +
            ", rawJson = " + rawJson +
            ", normalizedJson = " + normalizedJson +
            ", status = " + status +
            ", operationType = " + operationType +
            ", targetRecordId = " + targetRecordId +
            ", expectedRecordVersion = " + expectedRecordVersion +
            ", beforeSnapshotJson = " + beforeSnapshotJson +
            ", appliedRecordVersion = " + appliedRecordVersion +
            ", rollbackRecordVersion = " + rollbackRecordVersion +
            ", rollbackStatus = " + rollbackStatus +
            ", rollbackTime = " + rollbackTime +
            ", errorJson = " + errorJson +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
