package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批高级规则适用人员
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_examine_config_user")
public class UnExamineConfigUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批规则适用对象主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批高级配置主键
     */
    @TableField("config_id")
    private Long configId;

    /**
     * 适用人员部门或角色类型
     */
    @TableField("subject_type")
    private String subjectType;

    /**
     * 适用对象主键
     */
    @TableField("subject_id")
    private Long subjectId;

    /**
     * 配置时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
