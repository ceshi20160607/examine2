package com.unique.unexamine.system.manage;

/** 当前账号在指定系统内可进入的启用租户，供运行态切换使用。 */
public record AccessibleTenant(Long id, String code, String name, boolean main) {
}
