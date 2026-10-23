package com.unique.unexamine.sys.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 流程画布草稿节点
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_flow_node")
public class UnFlowNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程草稿节点主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 流程内稳定节点编码
     */
    @TableField("node_code")
    private String nodeCode;

    /**
     * 审批条件网关自动等待外部等节点类型
     */
    @TableField("node_type")
    private String nodeType;

    /**
     * 节点展示名称
     */
    @TableField("name")
    private String name;

    /**
     * 设计器节点位置和尺寸
     */
    @TableField("position_json")
    private String positionJson;

    /**
     * 草稿审批人员字段权限超时和执行配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 设计器稳定节点顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
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

    public String getPositionJson() {
        return positionJson;
    }

    public void setPositionJson(String positionJson) {
        this.positionJson = positionJson;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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
