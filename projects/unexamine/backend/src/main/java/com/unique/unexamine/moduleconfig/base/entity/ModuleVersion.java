package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块不可变发布快照
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_module_version")
public class ModuleVersion {

    /**
     * 模块发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 模块内连续发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 发布时字段、菜单和动作的不可变配置快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 执行发布的平台账号主键
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 模块版本发布时间
     */
    @TableField("published_time")
    private LocalDateTime publishedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
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
        return "ModuleVersion{" +
            "id = " + id +
            ", moduleId = " + moduleId +
            ", versionNumber = " + versionNumber +
            ", snapshotJson = " + snapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishedTime = " + publishedTime +
            "}";
    }
}
