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
 * 审批节点处理人员配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_examine_node_user")
public class UnExamineNodeUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批节点人员配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批草稿节点主键
     */
    @TableField("node_id")
    private Long nodeId;

    /**
     * 人员部门角色上级或外部身份来源类型
     */
    @TableField("subject_type")
    private String subjectType;

    /**
     * 内部人员部门或角色主键
     */
    @TableField("subject_id")
    private Long subjectId;

    /**
     * 外部审批人配置标识
     */
    @TableField("external_id")
    private String externalId;

    /**
     * 顺序会签的处理顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 上级截止人及人员解析补充规则
     */
    @TableField("config_json")
    private String configJson;

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

    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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
