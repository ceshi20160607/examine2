package com.unique.unexamine.flow.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

/**
 * 审批节点的附件要求随发布版本冻结；旧版本没有该配置时保持不必填。
 */
public record FlowApprovalAttachmentPolicy(boolean required, int minimumCount) {
    public static final String CONFIG_KEY = "attachmentPolicy";

    public static FlowApprovalAttachmentPolicy fromConfig(Map<String, Object> config) {
        Object configured = config == null ? null : config.get(CONFIG_KEY);
        if (configured == null) return new FlowApprovalAttachmentPolicy(false, 0);
        if (!(configured instanceof Map<?, ?> policy)
                || !policy.keySet().equals(Set.of("required", "minimumCount"))
                || !Boolean.TRUE.equals(policy.get("required"))
                || !(policy.get("minimumCount") instanceof Number count)
                || count.doubleValue() != count.intValue()
                || count.intValue() < 1 || count.intValue() > 10) {
            throw new DomainException("FLOW_APPROVAL_ATTACHMENT_POLICY_INVALID",
                    "审批附件要求无效，请使用节点中的附件必填开关重新配置",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return new FlowApprovalAttachmentPolicy(true, count.intValue());
    }
}
