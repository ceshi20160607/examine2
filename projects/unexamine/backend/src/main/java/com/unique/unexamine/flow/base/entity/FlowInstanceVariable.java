package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 流程实例变量
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_instance_variable")
public class FlowInstanceVariable {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例ID
     */
    @TableField("instance_id")
    private Long instanceId;

    /**
     * 实例变量编码
     */
    @TableField("variable_key")
    private String variableKey;

    /**
     * 实例变量值类型
     */
    @TableField("value_type")
    private String valueType;

    /**
     * 实例变量JSON值
     */
    @TableField("value_json")
    private String valueJson;

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

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public String getVariableKey() {
        return variableKey;
    }

    public void setVariableKey(String variableKey) {
        this.variableKey = variableKey;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
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
        return "FlowInstanceVariable{" +
            "id = " + id +
            ", instanceId = " + instanceId +
            ", variableKey = " + variableKey +
            ", valueType = " + valueType +
            ", valueJson = " + valueJson +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
