package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统运行态模块分组
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_module_group")
public class ModuleGroup {

    /**
     * 模块组主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模块组所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统内模块组编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 运行态顶栏模块组名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 模块组显示顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 模块组启用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 模块组创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 模块组更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    @Override
    public String toString() {
        return "ModuleGroup{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", code = " + code +
            ", name = " + name +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
