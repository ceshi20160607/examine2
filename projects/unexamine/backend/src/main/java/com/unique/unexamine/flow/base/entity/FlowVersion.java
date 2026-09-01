package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("flow_version")
public class FlowVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_id")
    private Long flowId;

    @TableField("version_number")
    private Integer versionNumber;

    @TableField("draft_revision")
    private Integer draftRevision;

    @TableField("definition_hash")
    private String definitionHash;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("simulation_result_json")
    private String simulationResultJson;

    @TableField("change_summary")
    private String changeSummary;

    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    @TableField("published_at")
    private LocalDateTime publishedAt;

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

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
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
            ", publishedAt = " + publishedAt +
            "}";
    }
}
