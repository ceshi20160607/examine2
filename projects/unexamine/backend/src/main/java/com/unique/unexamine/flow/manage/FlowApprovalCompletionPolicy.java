package com.unique.unexamine.flow.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

/** 会签完成条件的唯一解释器；旧版本缺省时保持全部同意。 */
public record FlowApprovalCompletionPolicy(String mode, int ratioPercent) {
    public static final String CONFIG_KEY = "allSignCompletionPolicy";

    public static FlowApprovalCompletionPolicy fromConfig(Map<String, Object> config, String approvalMode) {
        if (!config.containsKey(CONFIG_KEY)) return new FlowApprovalCompletionPolicy("ALL", 100);
        if (!"ALL_SIGN".equals(approvalMode) || !(config.get(CONFIG_KEY) instanceof Map<?, ?> policy)
                || !(policy.get("mode") instanceof String mode) || !Set.of("ALL", "RATIO").contains(mode)
                || "ALL".equals(mode) && !policy.keySet().equals(Set.of("mode"))
                || "RATIO".equals(mode) && !policy.keySet().equals(Set.of("mode", "ratioPercent"))) {
            throw invalid("会签完成条件仅适用于会签，必须选择全部同意或达到比例");
        }
        if ("ALL".equals(mode)) return new FlowApprovalCompletionPolicy(mode, 100);
        if (!(policy.get("ratioPercent") instanceof Integer ratio) || ratio < 1 || ratio > 100) {
            throw invalid("会签通过比例必须是1至100的整数");
        }
        return new FlowApprovalCompletionPolicy(mode, ratio);
    }

    public Map<String, Object> snapshot() {
        return "ALL".equals(mode) ? Map.of("mode", "ALL") : Map.of("mode", "RATIO", "ratioPercent", ratioPercent);
    }

    public boolean reached(int approved, int total) {
        return total > 0 && approved >= (int) Math.ceil(total * ratioPercent / 100.0d);
    }

    private static DomainException invalid(String message) {
        return new DomainException("FLOW_ALL_SIGN_COMPLETION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
