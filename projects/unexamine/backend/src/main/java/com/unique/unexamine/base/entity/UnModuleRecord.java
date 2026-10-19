package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块通用业务主记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_record")
public class UnModuleRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块业务记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 记录所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 记录显示名称
     */
    @TableField("name")
    private String name;

    /**
     * 模块内业务编号
     */
    @TableField("num")
    private String num;

    /**
     * 记录备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 记录状态：0 草稿，1 正常，2 已删除
     */
    @TableField("record_status")
    private Byte recordStatus;

    /**
     * 记录负责人主键
     */
    @TableField("owner_member_id")
    private Long ownerMemberId;

    /**
     * 记录归属部门主键
     */
    @TableField("owner_department_id")
    private Long ownerDepartmentId;

    /**
     * 当前关联的审批实例主键
     */
    @TableField("examine_example_id")
    private Long examineExampleId;

    /**
     * 当前关联的流程实例主键
     */
    @TableField("flow_example_id")
    private Long flowExampleId;

    /**
     * 更新版本号，用于避免并发覆盖
     */
    @TableField("version")
    private Integer version;

    /**
     * 创建人成员主键
     */
    @TableField("create_member_id")
    private Long createMemberId;

    /**
     * 最近修改人成员主键
     */
    @TableField("update_member_id")
    private Long updateMemberId;

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
     * 逻辑删除时间
     */
    @TableField("delete_time")
    private LocalDateTime deleteTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNum() {
        return num;
    }

    public void setNum(String num) {
        this.num = num;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Byte getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(Byte recordStatus) {
        this.recordStatus = recordStatus;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    public Long getOwnerDepartmentId() {
        return ownerDepartmentId;
    }

    public void setOwnerDepartmentId(Long ownerDepartmentId) {
        this.ownerDepartmentId = ownerDepartmentId;
    }

    public Long getExamineExampleId() {
        return examineExampleId;
    }

    public void setExamineExampleId(Long examineExampleId) {
        this.examineExampleId = examineExampleId;
    }

    public Long getFlowExampleId() {
        return flowExampleId;
    }

    public void setFlowExampleId(Long flowExampleId) {
        this.flowExampleId = flowExampleId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getCreateMemberId() {
        return createMemberId;
    }

    public void setCreateMemberId(Long createMemberId) {
        this.createMemberId = createMemberId;
    }

    public Long getUpdateMemberId() {
        return updateMemberId;
    }

    public void setUpdateMemberId(Long updateMemberId) {
        this.updateMemberId = updateMemberId;
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

    public LocalDateTime getDeleteTime() {
        return deleteTime;
    }

    public void setDeleteTime(LocalDateTime deleteTime) {
        this.deleteTime = deleteTime;
    }
}
