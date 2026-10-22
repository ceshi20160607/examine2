package com.unique.unexamine.sys.module.manage.enums;

/** 仅用于模块创建时初始化默认菜单；运行时授权以实际菜单 ID 为准。 */
public enum DefaultModuleAction {
    CREATE("新建","LIST_TOOLBAR",true), EDIT("编辑","ROW",true),
    LIST("列表","MODULE_ENTRY",true), DETAIL("详情","ROW",true),
    IMPORT("导入","LIST_TOOLBAR",false), EXPORT("导出","LIST_TOOLBAR",false),
    DELETE("删除","ROW",true), TRANSFER("转移","DETAIL_HEADER",true),
    PRINT("打印","DETAIL_HEADER",false), CONVERT("转化","DETAIL_HEADER",true);
    private final String label;
    private final String position;
    private final boolean enabled;
    DefaultModuleAction(String label,String position,boolean enabled) {
        this.label=label; this.position=position; this.enabled=enabled;
    }
    public String label() { return label; }
    public String position() { return position; }
    public boolean enabled() { return enabled; }
}
