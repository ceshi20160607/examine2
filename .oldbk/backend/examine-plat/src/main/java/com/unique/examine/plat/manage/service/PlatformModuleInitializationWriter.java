package com.unique.examine.plat.manage.service;

import java.time.LocalDateTime;
import java.util.List;

import com.unique.examine.plat.manage.vo.InitializedObjectVO;

/**
 * 系统创建时跨模块初始化写入器。
 */
public interface PlatformModuleInitializationWriter {

    /**
     * 初始化系统内默认成员、角色、权限目录和默认应用。
     *
     * @param command 初始化命令
     * @return 初始化结果
     */
    InitializationResult initialize(InitializationCommand command);

    /**
     * 跨模块初始化命令。
     *
     * @param systemId 系统 ID
     * @param tenantId 默认租户 ID
     * @param accountId 创建人平台账号 ID
     * @param displayName 创建人展示名称快照
     * @param now 当前业务时间
     */
    record InitializationCommand(
            Long systemId,
            Long tenantId,
            Long accountId,
            String displayName,
            LocalDateTime now
    ) {
    }

    /**
     * 跨模块初始化结果。
     *
     * @param ownerMemberId 创建人成员扩展 ID
     * @param initializedObjects 初始化对象
     */
    record InitializationResult(Long ownerMemberId, List<InitializedObjectVO> initializedObjects) {
    }
}
