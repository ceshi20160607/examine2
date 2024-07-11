package com.unique.admin.common.enums;

/**
 * 菜单类型
 *
 * @author UNIQUE
 * @date 2023/3/27
 */
public enum MenuTypeEnum {

    INDEX(0, "列表"),
    DETAIL(1, "详情"),
    ADD(2, "添加"),
    EDIT(3, "编辑"),
    DELETE(4, "删除"),
    IMPORT(5, "导入"),
    EXPORT(6, "导出"),
    PRINT(7, "打印"),

    STATUS(10, "修改某个字段/状态"),
    TRANSFER(11, "转化，模块下的数据转成另一个模块的数据"),

    NULL(-1,"异常")
    ;

    MenuTypeEnum(Integer type, String remarks) {
        this.type = type;
        this.remarks = remarks;
    }

    private final Integer type;
    private final String remarks;


    public Integer getType() {
        return type;
    }

    public String getRemarks() {
        return remarks;
    }

    public static MenuTypeEnum parse(Integer type) {
        for (MenuTypeEnum item : values()) {
            if (item.getType().equals(type)) {
                return item;
            }
        }
        return NULL;
    }
}