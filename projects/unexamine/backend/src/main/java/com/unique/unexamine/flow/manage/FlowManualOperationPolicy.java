package com.unique.unexamine.flow.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 人工按钮及意见要求的唯一解释器；读取已冻结节点配置，不影响超时或重复审批自动策略。 */
public record FlowManualOperationPolicy(List<String> allowedActions, List<String> opinionRequiredActions, boolean requireClaim) {
    public static final String CONFIG_KEY = "manualOperationPolicy";
    /** 旧版本没有显式按钮配置时，不因新增能力自动获得加减签或委托权限。 */
    public static final List<String> LEGACY_ACTIONS = List.of("APPROVE", "REJECT", "RETURN", "TRANSFER");
    public static final List<String> SUPPORTED_ACTIONS = List.of(
            "APPROVE", "REJECT", "RETURN", "TRANSFER", "DELEGATE", "ADD_SIGN", "REMOVE_SIGN");
    private static final Set<String> REQUIRED_REASONS = Set.of("REJECT", "RETURN", "DELEGATE");

    /** 旧发布版本未设置策略时保持原四项操作及拒绝、退回必填原因的语义。 */
    public static FlowManualOperationPolicy fromConfig(Map<String, Object> config) {
        if (!config.containsKey(CONFIG_KEY)) {
            return new FlowManualOperationPolicy(LEGACY_ACTIONS, List.of("REJECT", "RETURN"), false);
        }
        if (!(config.get(CONFIG_KEY) instanceof Map<?, ?> policy)
                || !policy.keySet().equals(Set.of("allowedActions", "opinionRequiredActions"))
                && !policy.keySet().equals(Set.of("allowedActions", "opinionRequiredActions", "requireClaim"))) {
            throw invalid("人工操作配置必须包含允许操作和必填意见两项");
        }
        List<String> allowed = actions(policy.get("allowedActions"));
        List<String> required = actions(policy.get("opinionRequiredActions"));
        if (allowed.isEmpty()) throw invalid("人工节点至少选择一种允许操作");
        if (!allowed.containsAll(required)) throw invalid("必填意见只能配置在已允许的操作上");
        if (allowed.stream().anyMatch(action -> REQUIRED_REASONS.contains(action) && !required.contains(action))) {
            throw invalid("已允许的拒绝、退回或委托操作必须填写原因，不能关闭该要求");
        }
        if (policy.containsKey("requireClaim") && !(policy.get("requireClaim") instanceof Boolean)) {
            throw invalid("是否必须领取任务必须使用开关配置");
        }
        return new FlowManualOperationPolicy(allowed, required, Boolean.TRUE.equals(policy.get("requireClaim")));
    }

    /** 严格验证配置而非静默丢弃未知操作，统一以固定顺序向页面和动态节点投影。 */
    private static List<String> actions(Object value) {
        if (!(value instanceof List<?> list)) throw invalid("操作配置必须使用操作选项列表");
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String action) || !SUPPORTED_ACTIONS.contains(action) || !found.add(action)) {
                throw invalid("操作配置包含未知或重复的操作");
            }
        }
        return SUPPORTED_ACTIONS.stream().filter(found::contains).toList();
    }

    /** 转为内部冻结存储协议；新加节点继承规范化策略，不读取后来修改的模板草稿。 */
    public Map<String, Object> snapshot() {
        return Map.of("allowedActions", allowedActions, "opinionRequiredActions", opinionRequiredActions, "requireClaim", requireClaim);
    }

    /** 必须在任何任务、表单候选或历史写入之前检查。 */
    public void requireOperation(String action, String opinion) {
        if (!allowedActions.contains(action)) {
            throw new DomainException("FLOW_TASK_OPERATION_DISABLED", "该节点未允许此操作，请刷新当前待办", HttpStatus.FORBIDDEN);
        }
        if (opinionRequiredActions.contains(action) && (opinion == null || opinion.isBlank())) {
            throw new DomainException("FLOW_TASK_REASON_REQUIRED", "此操作必须填写处理意见或原因", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException("FLOW_MANUAL_OPERATION_POLICY_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
