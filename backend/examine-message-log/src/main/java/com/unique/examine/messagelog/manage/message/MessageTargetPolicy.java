package com.unique.examine.messagelog.manage.message;

import com.unique.examine.core.error.BusinessException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Shared message target boundary policy.
 */
public final class MessageTargetPolicy {

    public static final String PLATFORM_SCOPE = "platform";
    public static final String SYSTEM_SCOPE = "system";

    private static final Set<String> PLATFORM_TARGET_TYPES = Set.of(
            "system_switch",
            "platform_auth",
            "platform_task",
            "platform_log",
            "agent_result",
            "audit_log"
    );

    private static final Set<String> SYSTEM_TARGET_TYPES = Set.of(
            "business_record",
            "approval_task",
            "async_task",
            "todo",
            "work_item",
            "daily_report",
            "agent_result",
            "audit_log"
    );

    private MessageTargetPolicy() {
    }

    /**
     * Normalize a message scope value.
     *
     * @param scope raw scope
     * @return normalized scope
     */
    public static String normalizeScope(String scope) {
        if (!hasText(scope)) {
            return SYSTEM_SCOPE;
        }
        return scope.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Assert that a target type is allowed by message scope.
     *
     * @param scope message scope
     * @param targetType target type
     */
    public static void assertAllowed(String scope, String targetType) {
        String normalizedScope = normalizeScope(scope);
        if (!hasText(targetType)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_REQUIRED_FIELD, "消息目标类型不能为空");
        }
        String normalizedTargetType = targetType.trim().toLowerCase(Locale.ROOT);
        if (PLATFORM_SCOPE.equals(normalizedScope) && !PLATFORM_TARGET_TYPES.contains(normalizedTargetType)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_TARGET_FORBIDDEN,
                    "平台消息不能直接打开系统业务详情",
                    null,
                    "请改为 system_switch、platform_auth、platform_task、platform_log、agent_result 或 audit_log");
        }
        if (SYSTEM_SCOPE.equals(normalizedScope) && !SYSTEM_TARGET_TYPES.contains(normalizedTargetType)) {
            throw new BusinessException(MessageErrorCode.MESSAGE_TARGET_FORBIDDEN,
                    "系统消息目标类型不在当前系统成员上下文允许范围内",
                    null,
                    "系统消息仅可在 SystemSwitchContext/systemMemberId 下打开业务记录、审批任务、异步任务或系统工作对象");
        }
    }

    /**
     * Return whether text contains non-blank characters.
     *
     * @param value candidate value
     * @return true when value has text
     */
    public static boolean hasText(String value) {
        return Objects.nonNull(value) && !value.isBlank();
    }
}
