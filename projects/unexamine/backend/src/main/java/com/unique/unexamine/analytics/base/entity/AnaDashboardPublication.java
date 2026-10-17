package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 运行态仪表盘发布和回滚指针
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_dashboard_publication")
public class AnaDashboardPublication {

    /**
     * 仪表盘当前发布指针主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属仪表盘主键
     */
    @TableField("dashboard_id")
    private Long dashboardId;

    /**
     * 当前对运行态生效的仪表盘版本主键
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 最后发布或回滚操作者账号主键
     */
    @TableField("updated_by_account_id")
    private Long updatedByAccountId;

    /**
     * 发布指针更新时间
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

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
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
        return "AnaDashboardPublication{" +
            "id = " + id +
            ", dashboardId = " + dashboardId +
            ", currentVersionId = " + currentVersionId +
            ", updatedByAccountId = " + updatedByAccountId +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
