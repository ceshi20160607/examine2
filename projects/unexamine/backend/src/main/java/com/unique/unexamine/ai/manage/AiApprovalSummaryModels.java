package com.unique.unexamine.ai.manage;

import java.util.List;

/** 审批历史的可追溯 AI 摘要；事件编号对应原实例历史列表。 */
public final class AiApprovalSummaryModels {
    private AiApprovalSummaryModels() { }

    /** 一条摘要要点及其可在原审批历史中定位的依据。 */
    public record Point(String text, List<Long> sourceEventIds) { }

    /** 模型不可用时返回空摘要，调用页面继续展示原审批历史。 */
    public record Summary(Long instanceId, String status, String summary, List<Point> points,
                          boolean modelUsed, int sourceCount) { }
}
