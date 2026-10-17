package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 流程当前发布指针
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_publication")
public class FlowPublication {

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
     * 当前生效流程版本ID
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 最后修改人平台账号ID
     */
    @TableField("updated_by_account_id")
    private Long updatedByAccountId;

    /**
     * 最后修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField("version")
    private Integer version;

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

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public Long getUpdatedByAccountId() {
        return updatedByAccountId;
    }

    public void setUpdatedByAccountId(Long updatedByAccountId) {
        this.updatedByAccountId = updatedByAccountId;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "FlowPublication{" +
            "id = " + id +
            ", flowId = " + flowId +
            ", currentVersionId = " + currentVersionId +
            ", updatedByAccountId = " + updatedByAccountId +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
