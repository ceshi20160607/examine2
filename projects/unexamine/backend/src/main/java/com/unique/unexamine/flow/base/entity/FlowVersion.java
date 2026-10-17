package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变流程发布版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_version")
public class FlowVersion {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程定义ID
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 流程发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 发布定义内容摘要
     */
    @TableField("definition_hash")
    private String definitionHash;

    /**
     * 发布时冻结的完整流程定义快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布前模拟验证结果
     */
    @TableField("simulation_result_json")
    private String simulationResultJson;

    /**
     * 发布变更说明
     */
    @TableField("change_summary")
    private String changeSummary;

    /**
     * 发布人平台账号ID
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

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

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getDefinitionHash() {
        return definitionHash;
    }

    public void setDefinitionHash(String definitionHash) {
        this.definitionHash = definitionHash;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getSimulationResultJson() {
        return simulationResultJson;
    }

    public void setSimulationResultJson(String simulationResultJson) {
        this.simulationResultJson = simulationResultJson;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public Long getPublishedByAccountId() {
        return publishedByAccountId;
    }

    public void setPublishedByAccountId(Long publishedByAccountId) {
        this.publishedByAccountId = publishedByAccountId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    @Override
    public String toString() {
        return "FlowVersion{" +
            "id = " + id +
            ", flowId = " + flowId +
            ", versionNumber = " + versionNumber +
            ", draftRevision = " + draftRevision +
            ", definitionHash = " + definitionHash +
            ", snapshotJson = " + snapshotJson +
            ", simulationResultJson = " + simulationResultJson +
            ", changeSummary = " + changeSummary +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishTime = " + publishTime +
            "}";
    }
}
