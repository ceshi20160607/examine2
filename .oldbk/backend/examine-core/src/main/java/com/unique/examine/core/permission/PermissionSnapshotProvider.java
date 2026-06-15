package com.unique.examine.core.permission;

import com.unique.examine.core.context.RequestContext;

/**
 * 权限快照提供器。
 */
public interface PermissionSnapshotProvider {

    /**
     * 当前提供器是否支持该请求上下文。
     *
     * @param context 请求上下文
     * @return 是否支持
     */
    boolean supports(RequestContext context);

    /**
     * 读取有效权限快照。
     *
     * @param context 请求上下文
     * @return 有效权限
     */
    EffectivePermissionVO load(RequestContext context);
}
