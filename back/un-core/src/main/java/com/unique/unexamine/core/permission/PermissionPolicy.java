package com.unique.unexamine.core.permission;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 业务域加载角色和租户授权后，统一使用本结果过滤字段并校验写入与负责人范围。 */
public record PermissionPolicy(boolean actionAllowed, boolean allData, Set<Long> ownerAccountIds,
                               boolean allReadableFields, Set<String> readableFields,
                               boolean allWritableFields, Set<String> writableFields,
                               boolean allExportableFields, Set<String> exportableFields) {
    public PermissionPolicy {
        ownerAccountIds = Set.copyOf(ownerAccountIds);
        readableFields = Set.copyOf(readableFields);
        writableFields = Set.copyOf(writableFields);
        exportableFields = Set.copyOf(exportableFields);
    }

    /** 菜单或动作未正向授权时默认拒绝。 */
    public void requireAction() {
        if (!actionAllowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少当前模块动作权限");
    }

    /** 字段读取权限与租户硬限制已由加载方合并。 */
    public boolean readable(String field) { return allReadableFields || readableFields.contains(field); }
    /** 写权限不允许超过当前动作可读字段。 */
    public boolean writable(String field) { return readable(field) && (allWritableFields || writableFields.contains(field)); }
    /** 导出必须同时拥有字段读取与导出许可。 */
    public boolean exportable(String field) { return readable(field) && (allExportableFields || exportableFields.contains(field)); }

    /** 保留集合原顺序及对象配置，不触发布局重排。 */
    public <T> List<T> filterReadable(List<T> fields, Function<T, String> code) {
        requireAction();
        return fields.stream().filter(field -> readable(code.apply(field))).toList();
    }

    /** 无权写入的字段明确拒绝，不静默丢弃。 */
    public void requireWritable(Set<String> submitted) {
        requireAction();
        if (submitted.stream().anyMatch(field -> !writable(field))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "提交包含无权编辑的字段");
        }
    }

    /** 数据范围使用负责人账号，不能用创建人替代。 */
    public void requireOwner(Long ownerAccountId) {
        requireAction();
        if (!allData && !ownerAccountIds.contains(ownerAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "记录不在当前动作的数据范围内");
        }
    }
}
