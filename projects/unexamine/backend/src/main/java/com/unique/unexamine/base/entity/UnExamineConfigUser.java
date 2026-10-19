package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批配置适用人员与部门
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_examine_config_user")
public class UnExamineConfigUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批适用对象主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批定义主键
     */
    @TableField("examine_id")
    private Long examineId;

    /**
     * 适用对象类型：MEMBER 成员，DEPARTMENT 部门
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 适用成员或部门主键
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
