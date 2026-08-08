package com.unique.examine.flow.extension;

import java.util.List;

public final class FlowNodeCatalog {
    private FlowNodeCatalog() {
    }

    public enum Type {
        START,
        APPROVAL,
        CONDITIONAL_APPROVAL,
        COPY,
        CONDITIONAL_BRANCH,
        PARALLEL_GATEWAY,
        INCLUSIVE_GATEWAY,
        MERGE_GATEWAY,
        SUBFLOW,
        AUTOMATION,
        FORM,
        TASK,
        NOTIFICATION,
        FIELD_UPDATE,
        DATA_CREATE_UPDATE,
        WAIT,
        TIMER,
        MESSAGE,
        WEBHOOK,
        EXTERNAL,
        AI_ASSIST,
        END
    }

    public enum Family {
        CONTROL,
        HUMAN,
        COLLABORATION,
        RECORD,
        WAIT,
        DELIVERY,
        INTEGRATION,
        AI
    }

    public record Entry(
            Type type,
            String name,
            Family family,
            String executor,
            boolean requiresBusinessRecord,
            boolean requiresHumanContinuation
    ) {
        public Entry {
            if (type == null || family == null || name == null || name.isBlank()
                    || executor == null || executor.isBlank()) {
                throw new IllegalArgumentException("Flow node catalog entry is incomplete");
            }
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            entry(Type.START, "开始", Family.CONTROL, "start", false, false),
            entry(Type.APPROVAL, "普通审批", Family.HUMAN, "approval", true, true),
            entry(Type.CONDITIONAL_APPROVAL, "条件审批", Family.HUMAN,
                    "conditional-approval", true, true),
            entry(Type.COPY, "抄送", Family.COLLABORATION, "copy", false, false),
            entry(Type.CONDITIONAL_BRANCH, "条件分支", Family.CONTROL,
                    "conditional-route", false, false),
            entry(Type.PARALLEL_GATEWAY, "并行网关", Family.CONTROL,
                    "parallel-split", false, false),
            entry(Type.INCLUSIVE_GATEWAY, "包容网关", Family.CONTROL,
                    "inclusive-split", false, false),
            entry(Type.MERGE_GATEWAY, "合并网关", Family.CONTROL,
                    "branch-join", false, false),
            entry(Type.SUBFLOW, "子流程", Family.INTEGRATION, "subflow", false, false),
            entry(Type.AUTOMATION, "自动节点", Family.RECORD, "automation", false, false),
            entry(Type.FORM, "填写表单", Family.HUMAN, "business-form", true, true),
            entry(Type.TASK, "任务", Family.HUMAN, "human-task", false, true),
            entry(Type.NOTIFICATION, "通知", Family.DELIVERY, "notification", false, false),
            entry(Type.FIELD_UPDATE, "字段更新", Family.RECORD, "record-field-update", true, false),
            entry(Type.DATA_CREATE_UPDATE, "数据创建/更新", Family.RECORD,
                    "record-create-update", false, false),
            entry(Type.WAIT, "等待", Family.WAIT, "event-wait", false, false),
            entry(Type.TIMER, "定时器", Family.WAIT, "timer-wait", false, false),
            entry(Type.MESSAGE, "消息", Family.DELIVERY, "message", false, false),
            entry(Type.WEBHOOK, "Webhook", Family.INTEGRATION, "webhook", false, false),
            entry(Type.EXTERNAL, "外部节点", Family.INTEGRATION, "external-task", false, false),
            entry(Type.AI_ASSIST, "AI 辅助", Family.AI, "ai-assist-confirmation", true, true),
            entry(Type.END, "结束", Family.CONTROL, "end", false, false)
    );

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry require(Type type) {
        return ENTRIES.stream().filter(entry -> entry.type() == type)
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported Flow node type: " + type));
    }

    private static Entry entry(
            Type type, String name, Family family, String executor,
            boolean record, boolean human) {
        return new Entry(type, name, family, executor, record, human);
    }
}
