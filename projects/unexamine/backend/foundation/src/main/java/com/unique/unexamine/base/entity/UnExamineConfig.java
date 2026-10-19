package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批高级配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_examine_config")
public class UnExamineConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批高级配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批定义主键
     */
    @TableField("examine_id")
    private Long examineId;

    /**
     * 可发起范围：ALL 全租户，SPECIFIED 指定对象
     */
    @TableField("apply_scope")
    private String applyScope;

    /**
     * 处理中是否允许发起人撤回：1 允许，0 不允许
     */
    @TableField("allow_withdraw")
    private Byte allowWithdraw;

    /**
     * 重新发起方式：FIRST 从首节点开始，REJECTED 从拒绝节点开始
     */
    @TableField("restart_mode")
    private String restartMode;

    /**
     * 审批超时时长，空值表示不启用超时
     */
    @TableField("timeout_minutes")
    private Integer timeoutMinutes;

    /**
     * 超时动作：REMIND 提醒，PASS 自动通过，TRANSFER 转交
     */
    @TableField("timeout_action")
    private String timeoutAction;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExamineId() {
        return examineId;
    }

    public void setExamineId(Long examineId) {
        this.examineId = examineId;
    }

    public String getApplyScope() {
        return applyScope;
    }

    public void setApplyScope(String applyScope) {
        this.applyScope = applyScope;
    }

    public Byte getAllowWithdraw() {
        return allowWithdraw;
    }

    public void setAllowWithdraw(Byte allowWithdraw) {
        this.allowWithdraw = allowWithdraw;
    }

    public String getRestartMode() {
        return restartMode;
    }

    public void setRestartMode(String restartMode) {
        this.restartMode = restartMode;
    }

    public Integer getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(Integer timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public String getTimeoutAction() {
        return timeoutAction;
    }

    public void setTimeoutAction(String timeoutAction) {
        this.timeoutAction = timeoutAction;
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
}
