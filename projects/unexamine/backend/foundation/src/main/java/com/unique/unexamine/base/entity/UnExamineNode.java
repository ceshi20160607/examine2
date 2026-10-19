package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批节点
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_examine_node")
public class UnExamineNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批节点主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批定义主键
     */
    @TableField("examine_id")
    private Long examineId;

    /**
     * 审批定义内稳定节点标识
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 节点名称
     */
    @TableField("name")
    private String name;

    /**
     * 节点类型：APPROVE 审批，CONDITION 条件，COPY 抄送
     */
    @TableField("node_type")
    private String nodeType;

    /**
     * 节点执行顺序
     */
    @TableField("sort_num")
    private Integer sortNum;

    /**
     * 进入节点的业务字段条件，空值表示直接进入
     */
    @TableField("condition_rule")
    private String conditionRule;

    /**
     * 审批人来源：MEMBER 固定成员，DEPARTMENT 部门，ROLE 角色，SUPERIOR 上级，STARTER_CHOICE 发起人自选
     */
    @TableField("approver_type")
    private String approverType;

    /**
     * 多人通过方式：ANY 任一人，ALL 全部，SEQUENTIAL 依次
     */
    @TableField("pass_mode")
    private String passMode;

    /**
     * 无法解析上级时的兜底审批成员主键
     */
    @TableField("fallback_member_id")
    private Long fallbackMemberId;

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

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public Integer getSortNum() {
        return sortNum;
    }

    public void setSortNum(Integer sortNum) {
        this.sortNum = sortNum;
    }

    public String getConditionRule() {
        return conditionRule;
    }

    public void setConditionRule(String conditionRule) {
        this.conditionRule = conditionRule;
    }

    public String getApproverType() {
        return approverType;
    }

    public void setApproverType(String approverType) {
        this.approverType = approverType;
    }

    public String getPassMode() {
        return passMode;
    }

    public void setPassMode(String passMode) {
        this.passMode = passMode;
    }

    public Long getFallbackMemberId() {
        return fallbackMemberId;
    }

    public void setFallbackMemberId(Long fallbackMemberId) {
        this.fallbackMemberId = fallbackMemberId;
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
