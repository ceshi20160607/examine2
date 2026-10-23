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
 * 审批设计器节点
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_examine_node")
public class UnExamineNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批草稿节点主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批定义主键
     */
    @TableField("examine_id")
    private Long examineId;

    /**
     * 审批定义内稳定节点编码
     */
    @TableField("node_code")
    private String nodeCode;

    /**
     * 开始审批条件抄送动态或结束节点类型
     */
    @TableField("node_type")
    private String nodeType;

    /**
     * 节点展示名称
     */
    @TableField("name")
    private String name;

    /**
     * 默认上游审批节点主键
     */
    @TableField("before_node_id")
    private Long beforeNodeId;

    /**
     * 默认下游审批节点主键
     */
    @TableField("after_node_id")
    private Long afterNodeId;

    /**
     * 设计器节点顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 草稿审批方式条件转交加签和超时配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

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

    public Long getExamineId() {
        return examineId;
    }

    public void setExamineId(Long examineId) {
        this.examineId = examineId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getBeforeNodeId() {
        return beforeNodeId;
    }

    public void setBeforeNodeId(Long beforeNodeId) {
        this.beforeNodeId = beforeNodeId;
    }

    public Long getAfterNodeId() {
        return afterNodeId;
    }

    public void setAfterNodeId(Long afterNodeId) {
        this.afterNodeId = afterNodeId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
