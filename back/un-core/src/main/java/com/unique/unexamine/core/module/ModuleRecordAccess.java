package com.unique.unexamine.core.module;

import java.util.Map;
import java.util.List;

/**
 * 跨业务模块读取模块记录权限的端口。
 *
 * <p>审批模块只依赖此端口，不直接依赖系统模块，实际授权计算仍由 un-sys 的权限业务服务完成。</p>
 */
@FunctionalInterface
public interface ModuleRecordAccess {
    /** 校验当前账号可以在指定模块记录上执行后续业务。 */
    Access require(String token, Long systemId, Long tenantId, Long moduleId, Long recordId);

    /**
     * 校验审批运行只需要的业务边界。
     *
     * <p>审批节点的可见和可编辑字段来自发起时快照，不能因为办理人没有模块详情动作
     * 而被业务数据权限再次拦截；实现方仍必须核对系统成员、租户、模块、记录和逻辑删除。</p>
     */
    default Access requireProcessBoundary(String token, Long systemId, Long tenantId,
                                          Long moduleId, Long recordId) {
        return require(token, systemId, tenantId, moduleId, recordId);
    }

    /** 校验审批工作台使用的系统与租户上下文，不依赖某一条业务记录。 */
    default Access requireProcessContext(String token, Long systemId, Long tenantId) {
        throw new UnsupportedOperationException("当前模块未提供审批上下文校验能力");
    }

    /** 外部审批待办批量读取当前未删除记录的真实归属；缺失或跨租户记录不返回。 */
    default List<RecordOwner> activeRecordOwners(Long systemId, Long tenantId, List<Long> recordIds) {
        throw new UnsupportedOperationException("当前模块未提供审批待办记录归属查询能力");
    }

    /** 按审批节点允许的字段写回业务记录；实现方继续执行模块字段校验、权限和日志。 */
    default void update(String token, Long systemId, Long tenantId, Long moduleId, Long recordId,
                        Map<String, Object> values) {
        throw new UnsupportedOperationException("当前模块未提供审批字段写回能力");
    }

    /** 审批节点修改先做字段类型、关联和唯一性校验，但不写入业务主记录。 */
    default void validateProcessValues(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                       Long accountId, Map<String, Object> values) {
        // 平台审批没有业务记录；系统模块适配器按字段配置提供具体校验。
    }

    /** 已完成模块和记录边界检查的登录上下文。 */
    record Access(Long systemId, Long tenantId, Long accountId, boolean owner, boolean tenantAdmin) {}

    /** 审批待办关联记录的模块与负责人快照，以查询时的有效业务记录为准。 */
    record RecordOwner(Long recordId, Long moduleId, Long ownerAccountId, Long ownerDeptId) {}
}
