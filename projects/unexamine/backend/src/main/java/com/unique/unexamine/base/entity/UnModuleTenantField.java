package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 租户对字段的扩展记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_tenant_field")
public class UnModuleTenantField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户字段扩展主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 被覆盖的基础字段主键，空值表示租户新增字段
     */
    @TableField("base_field_id")
    private Long baseFieldId;

    /**
     * 有效字段英文编码
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 租户内字段显示名称
     */
    @TableField("name")
    private String name;

    /**
     * 租户新增字段类型；覆盖基础字段时沿用原类型
     */
    @TableField("type")
    private String type;

    /**
     * 租户字段业务说明
     */
    @TableField("remark")
    private String remark;

    /**
     * 租户表单输入提示
     */
    @TableField("input_tips")
    private String inputTips;

    /**
     * 新增文本字段的最大字符数
     */
    @TableField("max_length")
    private Integer maxLength;

    /**
     * 租户表单默认值
     */
    @TableField("default_value")
    private String defaultValue;

    /**
     * 租户字段值是否唯一：1 是，0 否
     */
    @TableField("union_flag")
    private Byte unionFlag;

    /**
     * 租户表单是否必填：1 是，0 否
     */
    @TableField("must_flag")
    private Byte mustFlag;

    /**
     * 租户内是否隐藏：1 是，0 否
     */
    @TableField("hidden_flag")
    private Byte hiddenFlag;

    /**
     * 租户新建表单是否显示：1 是，0 否
     */
    @TableField("add_flag")
    private Byte addFlag;

    /**
     * 租户默认列表是否显示：1 是，0 否
     */
    @TableField("index_flag")
    private Byte indexFlag;

    /**
     * 租户详情是否显示：1 是，0 否
     */
    @TableField("detail_flag")
    private Byte detailFlag;

    /**
     * 租户列表是否允许排序：1 是，0 否
     */
    @TableField("sortable_flag")
    private Byte sortableFlag;

    /**
     * 租户字段引用的字典编码
     */
    @TableField("dict_code")
    private String dictCode;

    /**
     * 租户字段选项与显示配置
     */
    @TableField("option_data")
    private String optionData;

    /**
     * 租户数字字段保留的小数位数
     */
    @TableField("precision_scale")
    private Byte precisionScale;

    /**
     * 租户数字字段允许的最小值
     */
    @TableField("min_num_restrict")
    private BigDecimal minNumRestrict;

    /**
     * 租户数字字段允许的最大值
     */
    @TableField("max_num_restrict")
    private BigDecimal maxNumRestrict;

    /**
     * 租户字段状态：1 启用，0 停用
     */
    @TableField("status")
    private Byte status;

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

    public Long getBaseFieldId() {
        return baseFieldId;
    }

    public void setBaseFieldId(Long baseFieldId) {
        this.baseFieldId = baseFieldId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getInputTips() {
        return inputTips;
    }

    public void setInputTips(String inputTips) {
        this.inputTips = inputTips;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Byte getUnionFlag() {
        return unionFlag;
    }

    public void setUnionFlag(Byte unionFlag) {
        this.unionFlag = unionFlag;
    }

    public Byte getMustFlag() {
        return mustFlag;
    }

    public void setMustFlag(Byte mustFlag) {
        this.mustFlag = mustFlag;
    }

    public Byte getHiddenFlag() {
        return hiddenFlag;
    }

    public void setHiddenFlag(Byte hiddenFlag) {
        this.hiddenFlag = hiddenFlag;
    }

    public Byte getAddFlag() {
        return addFlag;
    }

    public void setAddFlag(Byte addFlag) {
        this.addFlag = addFlag;
    }

    public Byte getIndexFlag() {
        return indexFlag;
    }

    public void setIndexFlag(Byte indexFlag) {
        this.indexFlag = indexFlag;
    }

    public Byte getDetailFlag() {
        return detailFlag;
    }

    public void setDetailFlag(Byte detailFlag) {
        this.detailFlag = detailFlag;
    }

    public Byte getSortableFlag() {
        return sortableFlag;
    }

    public void setSortableFlag(Byte sortableFlag) {
        this.sortableFlag = sortableFlag;
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getOptionData() {
        return optionData;
    }

    public void setOptionData(String optionData) {
        this.optionData = optionData;
    }

    public Byte getPrecisionScale() {
        return precisionScale;
    }

    public void setPrecisionScale(Byte precisionScale) {
        this.precisionScale = precisionScale;
    }

    public BigDecimal getMinNumRestrict() {
        return minNumRestrict;
    }

    public void setMinNumRestrict(BigDecimal minNumRestrict) {
        this.minNumRestrict = minNumRestrict;
    }

    public BigDecimal getMaxNumRestrict() {
        return maxNumRestrict;
    }

    public void setMaxNumRestrict(BigDecimal maxNumRestrict) {
        this.maxNumRestrict = maxNumRestrict;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
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
}
