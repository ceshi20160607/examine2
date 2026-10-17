package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块页面动作草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_module_action")
public class ModuleAction {

    /**
     * 模块动作主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 动作所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 新增、编辑、查看等动作编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 页面动作显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 模块动作的执行类别
     */
    @TableField("action_type")
    private String actionType;

    /**
     * 动作是否在发布后可用
     */
    @TableField("enabled_flag")
    private Boolean enabledFlag;

    /**
     * 动作在页面中的显示顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 动作创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 动作更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Boolean getEnabledFlag() {
        return enabledFlag;
    }

    public void setEnabledFlag(Boolean enabledFlag) {
        this.enabledFlag = enabledFlag;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    @Override
    public String toString() {
        return "ModuleAction{" +
            "id = " + id +
            ", moduleId = " + moduleId +
            ", code = " + code +
            ", name = " + name +
            ", actionType = " + actionType +
            ", enabledFlag = " + enabledFlag +
            ", sortOrder = " + sortOrder +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
