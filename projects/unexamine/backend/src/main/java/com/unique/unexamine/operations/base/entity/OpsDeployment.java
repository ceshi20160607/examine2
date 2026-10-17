package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 多环境部署、验证及回滚证据
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_deployment")
public class OpsDeployment {

    /**
     * 环境部署记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 目标产品发布版本主键
     */
    @TableField("release_id")
    private Long releaseId;

    /**
     * 开发、测试或生产等环境编码
     */
    @TableField("environment_code")
    private String environmentCode;

    /**
     * 部署或回滚操作类型
     */
    @TableField("deployment_type")
    private String deploymentType;

    /**
     * 部署前产品发布版本主键
     */
    @TableField("from_release_id")
    private Long fromReleaseId;

    /**
     * 部署执行结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 部署步骤及校验结果
     */
    @TableField("step_state_json")
    private String stepStateJson;

    /**
     * 可恢复的回滚点信息
     */
    @TableField("rollback_point_json")
    private String rollbackPointJson;

    /**
     * 发起部署的平台账号主键
     */
    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    /**
     * 部署开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 部署结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 部署申请时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 部署记录乐观锁版本
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

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public String getEnvironmentCode() {
        return environmentCode;
    }

    public void setEnvironmentCode(String environmentCode) {
        this.environmentCode = environmentCode;
    }

    public String getDeploymentType() {
        return deploymentType;
    }

    public void setDeploymentType(String deploymentType) {
        this.deploymentType = deploymentType;
    }

    public Long getFromReleaseId() {
        return fromReleaseId;
    }

    public void setFromReleaseId(Long fromReleaseId) {
        this.fromReleaseId = fromReleaseId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStepStateJson() {
        return stepStateJson;
    }

    public void setStepStateJson(String stepStateJson) {
        this.stepStateJson = stepStateJson;
    }

    public String getRollbackPointJson() {
        return rollbackPointJson;
    }

    public void setRollbackPointJson(String rollbackPointJson) {
        this.rollbackPointJson = rollbackPointJson;
    }

    public Long getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Long requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "OpsDeployment{" +
            "id = " + id +
            ", releaseId = " + releaseId +
            ", environmentCode = " + environmentCode +
            ", deploymentType = " + deploymentType +
            ", fromReleaseId = " + fromReleaseId +
            ", status = " + status +
            ", stepStateJson = " + stepStateJson +
            ", rollbackPointJson = " + rollbackPointJson +
            ", requestedByAccountId = " + requestedByAccountId +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", createTime = " + createTime +
            ", version = " + version +
            "}";
    }
}
