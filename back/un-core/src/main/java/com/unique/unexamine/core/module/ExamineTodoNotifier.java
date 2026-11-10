package com.unique.unexamine.core.module;

/** 审批节点生成内部账号待办后，通知所属平台或系统消息域。 */
public interface ExamineTodoNotifier {
    /** 使用审批实例定位业务详情，节点主键标识本次待办及去重范围。 */
    void notifyPending(Long systemId, Long tenantId, Long recipientAccountId,
            Long exampleId, Long nodeId, String nodeCode);
}
