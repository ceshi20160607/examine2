package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块数据导入导出及预演作业
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_module_exchange_job")
public class UnModuleExchangeJob implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块导入导出作业主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 操作业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 发起操作的平台账号主键
     */
    @TableField("operator_account_id")
    private Long operatorAccountId;

    /**
     * 导入或导出作业类型
     */
    @TableField("job_type")
    private String jobType;

    /**
     * 导入新增或匹配更新模式
     */
    @TableField("import_mode")
    private String importMode;

    /**
     * 预演执行成功失败或回滚状态
     */
    @TableField("status")
    private String status;

    /**
     * 提交时字段映射筛选和权限配置快照
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 导入来源公共文件主键
     */
    @TableField("source_file_id")
    private Long sourceFileId;

    /**
     * 导出结果公共文件主键
     */
    @TableField("result_file_id")
    private Long resultFileId;

    /**
     * 错误行下载公共文件主键
     */
    @TableField("error_file_id")
    private Long errorFileId;

    /**
     * 本批行总数
     */
    @TableField("total_count")
    private Integer totalCount;

    /**
     * 已成功处理行数
     */
    @TableField("success_count")
    private Integer successCount;

    /**
     * 失败行数
     */
    @TableField("failure_count")
    private Integer failureCount;

    /**
     * 作业创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 作业实际开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 作业完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 作业更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getOperatorAccountId() {
        return operatorAccountId;
    }

    public void setOperatorAccountId(Long operatorAccountId) {
        this.operatorAccountId = operatorAccountId;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getImportMode() {
        return importMode;
    }

    public void setImportMode(String importMode) {
        this.importMode = importMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public Long getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(Long sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public Long getResultFileId() {
        return resultFileId;
    }

    public void setResultFileId(Long resultFileId) {
        this.resultFileId = resultFileId;
    }

    public Long getErrorFileId() {
        return errorFileId;
    }

    public void setErrorFileId(Long errorFileId) {
        this.errorFileId = errorFileId;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(Integer failureCount) {
        this.failureCount = failureCount;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
