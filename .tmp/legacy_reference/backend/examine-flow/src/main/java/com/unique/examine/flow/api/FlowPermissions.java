package com.unique.examine.flow.api;

public final class FlowPermissions {
    public static final String DEFINITION_MANAGE = "flow.definition.manage";
    public static final String INSTANCE_START = "flow.instance.start";
    public static final String INSTANCE_DECIDE = "flow.instance.decide";
    public static final String INSTANCE_READ = "flow.instance.read";
    public static final String INSTANCE_WITHDRAW = "flow.instance.withdraw";
    public static final String INSTANCE_TERMINATE = "flow.instance.terminate";
    public static final String INSTANCE_URGE = "flow.instance.urge";
    public static final String INSTANCE_COMMENT = "flow.instance.comment";
    public static final String INSTANCE_TRANSFER = "flow.instance.transfer";
    public static final String INSTANCE_ADD_SIGN = "flow.instance.add-sign";
    public static final String INSTANCE_RETURN = "flow.instance.return";
    public static final String INSTANCE_CLAIM = "flow.instance.claim";
    public static final String INSTANCE_CANCEL_CLAIM = "flow.instance.cancel-claim";
    public static final String INSTANCE_REDUCE_SIGN = "flow.instance.reduce-sign";
    public static final String INSTANCE_COPY = "flow.instance.copy";
    public static final String DELEGATION_MANAGE = "flow.delegation.manage";
    public static final String EXTERNAL_TASK_WORK =
            "flow.external-task.work";

    private FlowPermissions() {
    }
}
