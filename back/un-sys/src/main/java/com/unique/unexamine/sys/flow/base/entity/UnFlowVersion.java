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
 * 不可变流程发布版本
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_flow_version")
public class UnFlowVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 不可变流程版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 流程发布版本号
     */
    @TableField("version_no")
    private Integer versionNo;

    /**
     * 节点连线条件字段权限和高级设置完整快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布快照内容摘要
     */
    @TableField("snapshot_hash")
    private String snapshotHash;

    /**
     * 发布流程的平台账号主键
     */
    @TableField("publish_account_id")
    private Long publishAccountId;

    /**
     * 流程版本发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

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

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String snapshotHash) {
        this.snapshotHash = snapshotHash;
    }

    public Long getPublishAccountId() {
        return publishAccountId;
    }

    public void setPublishAccountId(Long publishAccountId) {
        this.publishAccountId = publishAccountId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
