package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 审批流程不可变发布版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_version")
public class FlowVersion {

    /**
     * 流程发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 同流程内连续版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 发布时节点与审批人的不可变快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 执行发布的平台账号主键
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 版本发布时间
     */
    @TableField("published_time")
    private LocalDateTime publishedTime;

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

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public Long getPublishedByAccountId() {
        return publishedByAccountId;
    }

    public void setPublishedByAccountId(Long publishedByAccountId) {
        this.publishedByAccountId = publishedByAccountId;
    }

    public LocalDateTime getPublishedTime() {
        return publishedTime;
    }

    public void setPublishedTime(LocalDateTime publishedTime) {
        this.publishedTime = publishedTime;
    }

    @Override
    public String toString() {
        return "FlowVersion{" +
            "id = " + id +
            ", flowId = " + flowId +
            ", versionNumber = " + versionNumber +
            ", snapshotJson = " + snapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishedTime = " + publishedTime +
            "}";
    }
}
