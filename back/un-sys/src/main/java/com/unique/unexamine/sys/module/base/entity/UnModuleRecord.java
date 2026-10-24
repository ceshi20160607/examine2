package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块通用业务主记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-24
 */
@TableName("un_module_record")
public class UnModuleRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通用业务记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 记录原始所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 业务记录标题
     */
    @TableField("name")
    private String name;

    /**
     * 业务记录编号
     */
    @TableField("num")
    private String num;

    /**
     * 业务状态
     */
    @TableField("status")
    private String status;

    /**
     * 动态数字字段预留值一
     */
    @TableField("item_number01")
    private BigDecimal itemNumber01;

    /**
     * 动态数字字段预留值二
     */
    @TableField("item_number02")
    private BigDecimal itemNumber02;

    /**
     * 动态数字字段预留值三
     */
    @TableField("item_number03")
    private BigDecimal itemNumber03;

    /**
     * 动态数字字段预留值四
     */
    @TableField("item_number04")
    private BigDecimal itemNumber04;

    /**
     * 动态数字字段预留值五
     */
    @TableField("item_number05")
    private BigDecimal itemNumber05;

    /**
     * 动态数字字段预留值六
     */
    @TableField("item_number06")
    private BigDecimal itemNumber06;

    /**
     * 动态数字字段预留值七
     */
    @TableField("item_number07")
    private BigDecimal itemNumber07;

    /**
     * 动态数字字段预留值八
     */
    @TableField("item_number08")
    private BigDecimal itemNumber08;

    /**
     * 动态数字字段预留值九
     */
    @TableField("item_number09")
    private BigDecimal itemNumber09;

    /**
     * 动态数字字段预留值十
     */
    @TableField("item_number10")
    private BigDecimal itemNumber10;

    /**
     * 动态数字字段预留值十一
     */
    @TableField("item_number11")
    private BigDecimal itemNumber11;

    /**
     * 动态数字字段预留值十二
     */
    @TableField("item_number12")
    private BigDecimal itemNumber12;

    /**
     * 动态数字字段预留值十三
     */
    @TableField("item_number13")
    private BigDecimal itemNumber13;

    /**
     * 动态数字字段预留值十四
     */
    @TableField("item_number14")
    private BigDecimal itemNumber14;

    /**
     * 动态数字字段预留值十五
     */
    @TableField("item_number15")
    private BigDecimal itemNumber15;

    /**
     * 动态短文本字段预留值一
     */
    @TableField("item_text01")
    private String itemText01;

    /**
     * 动态短文本字段预留值二
     */
    @TableField("item_text02")
    private String itemText02;

    /**
     * 动态短文本字段预留值三
     */
    @TableField("item_text03")
    private String itemText03;

    /**
     * 动态短文本字段预留值四
     */
    @TableField("item_text04")
    private String itemText04;

    /**
     * 动态短文本字段预留值五
     */
    @TableField("item_text05")
    private String itemText05;

    /**
     * 动态短文本字段预留值六
     */
    @TableField("item_text06")
    private String itemText06;

    /**
     * 动态短文本字段预留值七
     */
    @TableField("item_text07")
    private String itemText07;

    /**
     * 动态短文本字段预留值八
     */
    @TableField("item_text08")
    private String itemText08;

    /**
     * 动态短文本字段预留值九
     */
    @TableField("item_text09")
    private String itemText09;

    /**
     * 动态短文本字段预留值十
     */
    @TableField("item_text10")
    private String itemText10;

    /**
     * 动态长文本字段预留值一
     */
    @TableField("item_long_text01")
    private String itemLongText01;

    /**
     * 动态长文本字段预留值二
     */
    @TableField("item_long_text02")
    private String itemLongText02;

    /**
     * 动态日期时间字段预留值一
     */
    @TableField("item_time01")
    private LocalDateTime itemTime01;

    /**
     * 动态日期时间字段预留值二
     */
    @TableField("item_time02")
    private LocalDateTime itemTime02;

    /**
     * 动态日期时间字段预留值三
     */
    @TableField("item_time03")
    private LocalDateTime itemTime03;

    /**
     * 动态日期时间字段预留值四
     */
    @TableField("item_time04")
    private LocalDateTime itemTime04;

    /**
     * 动态日期时间字段预留值五
     */
    @TableField("item_time05")
    private LocalDateTime itemTime05;

    /**
     * 动态日期时间字段预留值六
     */
    @TableField("item_time06")
    private LocalDateTime itemTime06;

    /**
     * 动态日期时间字段预留值七
     */
    @TableField("item_time07")
    private LocalDateTime itemTime07;

    /**
     * 动态日期时间字段预留值八
     */
    @TableField("item_time08")
    private LocalDateTime itemTime08;

    /**
     * 动态日期时间字段预留值九
     */
    @TableField("item_time09")
    private LocalDateTime itemTime09;

    /**
     * 动态日期时间字段预留值十
     */
    @TableField("item_time10")
    private LocalDateTime itemTime10;

    /**
     * 动态人员字段预留值一
     */
    @TableField("item_user01")
    private Long itemUser01;

    /**
     * 动态人员字段预留值二
     */
    @TableField("item_user02")
    private Long itemUser02;

    /**
     * 动态人员字段预留值三
     */
    @TableField("item_user03")
    private Long itemUser03;

    /**
     * 动态人员字段预留值四
     */
    @TableField("item_user04")
    private Long itemUser04;

    /**
     * 动态人员字段预留值五
     */
    @TableField("item_user05")
    private Long itemUser05;

    /**
     * 动态部门字段预留值一
     */
    @TableField("item_dept01")
    private Long itemDept01;

    /**
     * 动态部门字段预留值二
     */
    @TableField("item_dept02")
    private Long itemDept02;

    /**
     * 动态部门字段预留值三
     */
    @TableField("item_dept03")
    private Long itemDept03;

    /**
     * 动态部门字段预留值四
     */
    @TableField("item_dept04")
    private Long itemDept04;

    /**
     * 动态部门字段预留值五
     */
    @TableField("item_dept05")
    private Long itemDept05;

    /**
     * 动态关联模块字段预留值一
     */
    @TableField("item_module01")
    private Long itemModule01;

    /**
     * 动态关联模块字段预留值二
     */
    @TableField("item_module02")
    private Long itemModule02;

    /**
     * 动态关联模块字段预留值三
     */
    @TableField("item_module03")
    private Long itemModule03;

    /**
     * 动态关联模块字段预留值四
     */
    @TableField("item_module04")
    private Long itemModule04;

    /**
     * 动态关联模块字段预留值五
     */
    @TableField("item_module05")
    private Long itemModule05;

    /**
     * 动态单选字段预留值一
     */
    @TableField("item_select01")
    private String itemSelect01;

    /**
     * 动态单选字段预留值二
     */
    @TableField("item_select02")
    private String itemSelect02;

    /**
     * 动态单选字段预留值三
     */
    @TableField("item_select03")
    private String itemSelect03;

    /**
     * 动态单选字段预留值四
     */
    @TableField("item_select04")
    private String itemSelect04;

    /**
     * 动态单选字段预留值五
     */
    @TableField("item_select05")
    private String itemSelect05;

    /**
     * 动态单选字段预留值六
     */
    @TableField("item_select06")
    private String itemSelect06;

    /**
     * 动态单选字段预留值七
     */
    @TableField("item_select07")
    private String itemSelect07;

    /**
     * 动态单选字段预留值八
     */
    @TableField("item_select08")
    private String itemSelect08;

    /**
     * 动态单选字段预留值九
     */
    @TableField("item_select09")
    private String itemSelect09;

    /**
     * 动态单选字段预留值十
     */
    @TableField("item_select10")
    private String itemSelect10;

    /**
     * 当前负责人平台账号主键
     */
    @TableField("owner_user_id")
    private Long ownerUserId;

    /**
     * 负责人在本租户所属部门主键
     */
    @TableField("owner_dept_id")
    private Long ownerDeptId;

    /**
     * 创建业务归属账号主键
     */
    @TableField("create_user_id")
    private Long createUserId;

    /**
     * 实际提交操作的账号主键
     */
    @TableField("actual_operator_id")
    private Long actualOperatorId;

    /**
     * 最近一次修改业务记录的平台账号主键
     */
    @TableField("update_user_id")
    private Long updateUserId;

    /**
     * 业务记录并发写入乐观锁版本
     */
    @TableField("lock_version")
    private Long lockVersion;

    /**
     * 业务记录归档时间
     */
    @TableField("archived_time")
    private LocalDateTime archivedTime;

    /**
     * 逻辑删除时间
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getItemNumber01() {
        return itemNumber01;
    }

    public void setItemNumber01(BigDecimal itemNumber01) {
        this.itemNumber01 = itemNumber01;
    }

    public BigDecimal getItemNumber02() {
        return itemNumber02;
    }

    public void setItemNumber02(BigDecimal itemNumber02) {
        this.itemNumber02 = itemNumber02;
    }

    public BigDecimal getItemNumber03() {
        return itemNumber03;
    }

    public void setItemNumber03(BigDecimal itemNumber03) {
        this.itemNumber03 = itemNumber03;
    }

    public BigDecimal getItemNumber04() {
        return itemNumber04;
    }

    public void setItemNumber04(BigDecimal itemNumber04) {
        this.itemNumber04 = itemNumber04;
    }

    public BigDecimal getItemNumber05() {
        return itemNumber05;
    }

    public void setItemNumber05(BigDecimal itemNumber05) {
        this.itemNumber05 = itemNumber05;
    }

    public BigDecimal getItemNumber06() {
        return itemNumber06;
    }

    public void setItemNumber06(BigDecimal itemNumber06) {
        this.itemNumber06 = itemNumber06;
    }

    public BigDecimal getItemNumber07() {
        return itemNumber07;
    }

    public void setItemNumber07(BigDecimal itemNumber07) {
        this.itemNumber07 = itemNumber07;
    }

    public BigDecimal getItemNumber08() {
        return itemNumber08;
    }

    public void setItemNumber08(BigDecimal itemNumber08) {
        this.itemNumber08 = itemNumber08;
    }

    public BigDecimal getItemNumber09() {
        return itemNumber09;
    }

    public void setItemNumber09(BigDecimal itemNumber09) {
        this.itemNumber09 = itemNumber09;
    }

    public BigDecimal getItemNumber10() {
        return itemNumber10;
    }

    public void setItemNumber10(BigDecimal itemNumber10) {
        this.itemNumber10 = itemNumber10;
    }

    public BigDecimal getItemNumber11() {
        return itemNumber11;
    }

    public void setItemNumber11(BigDecimal itemNumber11) {
        this.itemNumber11 = itemNumber11;
    }

    public BigDecimal getItemNumber12() {
        return itemNumber12;
    }

    public void setItemNumber12(BigDecimal itemNumber12) {
        this.itemNumber12 = itemNumber12;
    }

    public BigDecimal getItemNumber13() {
        return itemNumber13;
    }

    public void setItemNumber13(BigDecimal itemNumber13) {
        this.itemNumber13 = itemNumber13;
    }

    public BigDecimal getItemNumber14() {
        return itemNumber14;
    }

    public void setItemNumber14(BigDecimal itemNumber14) {
        this.itemNumber14 = itemNumber14;
    }

    public BigDecimal getItemNumber15() {
        return itemNumber15;
    }

    public void setItemNumber15(BigDecimal itemNumber15) {
        this.itemNumber15 = itemNumber15;
    }

    public String getItemText01() {
        return itemText01;
    }

    public void setItemText01(String itemText01) {
        this.itemText01 = itemText01;
    }

    public String getItemText02() {
        return itemText02;
    }

    public void setItemText02(String itemText02) {
        this.itemText02 = itemText02;
    }

    public String getItemText03() {
        return itemText03;
    }

    public void setItemText03(String itemText03) {
        this.itemText03 = itemText03;
    }

    public String getItemText04() {
        return itemText04;
    }

    public void setItemText04(String itemText04) {
        this.itemText04 = itemText04;
    }

    public String getItemText05() {
        return itemText05;
    }

    public void setItemText05(String itemText05) {
        this.itemText05 = itemText05;
    }

    public String getItemText06() {
        return itemText06;
    }

    public void setItemText06(String itemText06) {
        this.itemText06 = itemText06;
    }

    public String getItemText07() {
        return itemText07;
    }

    public void setItemText07(String itemText07) {
        this.itemText07 = itemText07;
    }

    public String getItemText08() {
        return itemText08;
    }

    public void setItemText08(String itemText08) {
        this.itemText08 = itemText08;
    }

    public String getItemText09() {
        return itemText09;
    }

    public void setItemText09(String itemText09) {
        this.itemText09 = itemText09;
    }

    public String getItemText10() {
        return itemText10;
    }

    public void setItemText10(String itemText10) {
        this.itemText10 = itemText10;
    }

    public String getItemLongText01() {
        return itemLongText01;
    }

    public void setItemLongText01(String itemLongText01) {
        this.itemLongText01 = itemLongText01;
    }

    public String getItemLongText02() {
        return itemLongText02;
    }

    public void setItemLongText02(String itemLongText02) {
        this.itemLongText02 = itemLongText02;
    }

    public LocalDateTime getItemTime01() {
        return itemTime01;
    }

    public void setItemTime01(LocalDateTime itemTime01) {
        this.itemTime01 = itemTime01;
    }

    public LocalDateTime getItemTime02() {
        return itemTime02;
    }

    public void setItemTime02(LocalDateTime itemTime02) {
        this.itemTime02 = itemTime02;
    }

    public LocalDateTime getItemTime03() {
        return itemTime03;
    }

    public void setItemTime03(LocalDateTime itemTime03) {
        this.itemTime03 = itemTime03;
    }

    public LocalDateTime getItemTime04() {
        return itemTime04;
    }

    public void setItemTime04(LocalDateTime itemTime04) {
        this.itemTime04 = itemTime04;
    }

    public LocalDateTime getItemTime05() {
        return itemTime05;
    }

    public void setItemTime05(LocalDateTime itemTime05) {
        this.itemTime05 = itemTime05;
    }

    public LocalDateTime getItemTime06() {
        return itemTime06;
    }

    public void setItemTime06(LocalDateTime itemTime06) {
        this.itemTime06 = itemTime06;
    }

    public LocalDateTime getItemTime07() {
        return itemTime07;
    }

    public void setItemTime07(LocalDateTime itemTime07) {
        this.itemTime07 = itemTime07;
    }

    public LocalDateTime getItemTime08() {
        return itemTime08;
    }

    public void setItemTime08(LocalDateTime itemTime08) {
        this.itemTime08 = itemTime08;
    }

    public LocalDateTime getItemTime09() {
        return itemTime09;
    }

    public void setItemTime09(LocalDateTime itemTime09) {
        this.itemTime09 = itemTime09;
    }

    public LocalDateTime getItemTime10() {
        return itemTime10;
    }

    public void setItemTime10(LocalDateTime itemTime10) {
        this.itemTime10 = itemTime10;
    }

    public Long getItemUser01() {
        return itemUser01;
    }

    public void setItemUser01(Long itemUser01) {
        this.itemUser01 = itemUser01;
    }

    public Long getItemUser02() {
        return itemUser02;
    }

    public void setItemUser02(Long itemUser02) {
        this.itemUser02 = itemUser02;
    }

    public Long getItemUser03() {
        return itemUser03;
    }

    public void setItemUser03(Long itemUser03) {
        this.itemUser03 = itemUser03;
    }

    public Long getItemUser04() {
        return itemUser04;
    }

    public void setItemUser04(Long itemUser04) {
        this.itemUser04 = itemUser04;
    }

    public Long getItemUser05() {
        return itemUser05;
    }

    public void setItemUser05(Long itemUser05) {
        this.itemUser05 = itemUser05;
    }

    public Long getItemDept01() {
        return itemDept01;
    }

    public void setItemDept01(Long itemDept01) {
        this.itemDept01 = itemDept01;
    }

    public Long getItemDept02() {
        return itemDept02;
    }

    public void setItemDept02(Long itemDept02) {
        this.itemDept02 = itemDept02;
    }

    public Long getItemDept03() {
        return itemDept03;
    }

    public void setItemDept03(Long itemDept03) {
        this.itemDept03 = itemDept03;
    }

    public Long getItemDept04() {
        return itemDept04;
    }

    public void setItemDept04(Long itemDept04) {
        this.itemDept04 = itemDept04;
    }

    public Long getItemDept05() {
        return itemDept05;
    }

    public void setItemDept05(Long itemDept05) {
        this.itemDept05 = itemDept05;
    }

    public Long getItemModule01() {
        return itemModule01;
    }

    public void setItemModule01(Long itemModule01) {
        this.itemModule01 = itemModule01;
    }

    public Long getItemModule02() {
        return itemModule02;
    }

    public void setItemModule02(Long itemModule02) {
        this.itemModule02 = itemModule02;
    }

    public Long getItemModule03() {
        return itemModule03;
    }

    public void setItemModule03(Long itemModule03) {
        this.itemModule03 = itemModule03;
    }

    public Long getItemModule04() {
        return itemModule04;
    }

    public void setItemModule04(Long itemModule04) {
        this.itemModule04 = itemModule04;
    }

    public Long getItemModule05() {
        return itemModule05;
    }

    public void setItemModule05(Long itemModule05) {
        this.itemModule05 = itemModule05;
    }

    public String getItemSelect01() {
        return itemSelect01;
    }

    public void setItemSelect01(String itemSelect01) {
        this.itemSelect01 = itemSelect01;
    }

    public String getItemSelect02() {
        return itemSelect02;
    }

    public void setItemSelect02(String itemSelect02) {
        this.itemSelect02 = itemSelect02;
    }

    public String getItemSelect03() {
        return itemSelect03;
    }

    public void setItemSelect03(String itemSelect03) {
        this.itemSelect03 = itemSelect03;
    }

    public String getItemSelect04() {
        return itemSelect04;
    }

    public void setItemSelect04(String itemSelect04) {
        this.itemSelect04 = itemSelect04;
    }

    public String getItemSelect05() {
        return itemSelect05;
    }

    public void setItemSelect05(String itemSelect05) {
        this.itemSelect05 = itemSelect05;
    }

    public String getItemSelect06() {
        return itemSelect06;
    }

    public void setItemSelect06(String itemSelect06) {
        this.itemSelect06 = itemSelect06;
    }

    public String getItemSelect07() {
        return itemSelect07;
    }

    public void setItemSelect07(String itemSelect07) {
        this.itemSelect07 = itemSelect07;
    }

    public String getItemSelect08() {
        return itemSelect08;
    }

    public void setItemSelect08(String itemSelect08) {
        this.itemSelect08 = itemSelect08;
    }

    public String getItemSelect09() {
        return itemSelect09;
    }

    public void setItemSelect09(String itemSelect09) {
        this.itemSelect09 = itemSelect09;
    }

    public String getItemSelect10() {
        return itemSelect10;
    }

    public void setItemSelect10(String itemSelect10) {
        this.itemSelect10 = itemSelect10;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getOwnerDeptId() {
        return ownerDeptId;
    }

    public void setOwnerDeptId(Long ownerDeptId) {
        this.ownerDeptId = ownerDeptId;
    }

    public Long getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    public Long getActualOperatorId() {
        return actualOperatorId;
    }

    public void setActualOperatorId(Long actualOperatorId) {
        this.actualOperatorId = actualOperatorId;
    }

    public Long getUpdateUserId() {
        return updateUserId;
    }

    public void setUpdateUserId(Long updateUserId) {
        this.updateUserId = updateUserId;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }

    public LocalDateTime getArchivedTime() {
        return archivedTime;
    }

    public void setArchivedTime(LocalDateTime archivedTime) {
        this.archivedTime = archivedTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
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
