package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批实例节点
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_examine_example_node")
public class UnExamineExampleNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批实例节点主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 来源审批定义节点主键
     */
    @TableField("definition_node_id")
    private Long definitionNodeId;

    /**
     * 发起时的节点标识
     */
    @TableField("node_key")
    private String nodeKey;

    /**
     * 发起时的节点名称
     */
    @TableField("name")
    private String name;

    /**
     * 发起时的节点类型
     */
    @TableField("node_type")
    private String nodeType;

    /**
     * 发起时的多人通过方式
     */
    @TableField("pass_mode")
    private String passMode;

    /**
     * 发起时的节点执行顺序
     */
    @TableField("sort_num")
    private Integer sortNum;

    /**
     * 该节点的执行轮次
     */
    @TableField("round_no")
    private Integer roundNo;

    /**
     * 节点状态：WAITING 未到达，PENDING 处理中，APPROVED 已同意，REJECTED 已拒绝，SKIPPED 已跳过，CANCELLED 已取消
     */
    @TableField("status")
    private String status;

    /**
     * 节点开始处理时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 节点处理结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

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

    public Long getExampleId() {
        return exampleId;
    }

    public void setExampleId(Long exampleId) {
        this.exampleId = exampleId;
    }

    public Long getDefinitionNodeId() {
        return definitionNodeId;
    }

    public void setDefinitionNodeId(Long definitionNodeId) {
        this.definitionNodeId = definitionNodeId;
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

    public String getPassMode() {
        return passMode;
    }

    public void setPassMode(String passMode) {
        this.passMode = passMode;
    }

    public Integer getSortNum() {
        return sortNum;
    }

    public void setSortNum(Integer sortNum) {
        this.sortNum = sortNum;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
