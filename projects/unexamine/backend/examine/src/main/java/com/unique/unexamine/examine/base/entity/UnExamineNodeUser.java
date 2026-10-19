package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批节点处理人员配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_examine_node_user")
public class UnExamineNodeUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批节点处理对象主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批节点主键
     */
    @TableField("node_id")
    private Long nodeId;

    /**
     * 处理对象类型：MEMBER 成员，DEPARTMENT 部门，ROLE 角色
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 处理成员、部门或角色主键
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 依次审批时的处理顺序
     */
    @TableField("sort_num")
    private Integer sortNum;

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

    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
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

    public Integer getSortNum() {
        return sortNum;
    }

    public void setSortNum(Integer sortNum) {
        this.sortNum = sortNum;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
