package com.unique.unexamine.sys.module.manage.dto;

import java.util.List;

/** 导入回滚先展示可回滚性，再由操作者明确提交整批回滚。 */
public final class ModuleImportRollbackDto {
    private ModuleImportRollbackDto() {}
    /** 不可回滚时列出具体行及原因，避免静默跳过部分成功记录。 */
    public record Check(Long jobId,boolean rollbackable,int affectedCount,List<String> reasons) {}
    /** 整批事务完成后的结果。 */
    public record Result(Long jobId,String status,int rolledBackCount) {}
}
