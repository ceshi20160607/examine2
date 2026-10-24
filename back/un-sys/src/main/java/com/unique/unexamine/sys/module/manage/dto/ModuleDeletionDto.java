package com.unique.unexamine.sys.module.manage.dto;

/** ModuleDeletion 的请求与返回模型。 */
public final class ModuleDeletionDto {
    private ModuleDeletionDto() {}

    /** 有数据时必须明确选择直接删除或使用已下载的完整备份；空模块允许直接删除。 */
    public enum Choice { EMPTY, DIRECT, EXPORTED }

public record DeleteRequest(Choice choice,Long exportJobId) {}

public record Check(Long moduleId,long recordCount,boolean choiceRequired) {}

public record Backup(Long jobId,Long fileId,long recordCount) {}

public record Deleted(Long moduleId,long recordCount) {}
}
