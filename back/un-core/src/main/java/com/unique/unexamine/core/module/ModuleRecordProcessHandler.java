package com.unique.unexamine.core.module;

/**
 * 模块记录保存后的扩展处理器。
 *
 * <p>实现放在具体业务模块中，例如 un-sys 的 Flow 和 un-examine 的审批。处理器只处理自己
 * 的配置，不在记录服务里硬编码各业务域。</p>
 */
@FunctionalInterface
public interface ModuleRecordProcessHandler {

    /** 按已保存记录启动配置的后续流程。 */
    void afterSave(ModuleRecordProcessContext context);

    /**
     * 按已完成的模块动作启动后续流程。
     *
     * <p>默认复用保存处理，兼容只关心记录保存的处理器；需要区分动作语义的处理器
     * 可以覆盖此入口。动作处理必须发生在业务记录和操作日志写入同一事务内。</p>
     */
    default void afterAction(ModuleRecordProcessContext context) {
        afterSave(context);
    }

    /** 导入回滚前检查本处理器是否已为该记录建立不可直接撤销的实例。 */
    default boolean hasAssociatedInstance(Long systemId,Long moduleId,Long recordId) { return false; }
}
