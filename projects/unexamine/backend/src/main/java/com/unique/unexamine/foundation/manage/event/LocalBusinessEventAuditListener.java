package com.unique.unexamine.foundation.manage.event;

import com.unique.unexamine.audit.manage.AuditRecorder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地事件的内建订阅者，留下可核对的投递审计。
 * 与发件箱状态同事务提交；订阅者失败时整体回滚，重试不会留下重复的成功审计。
 */
@Component
public class LocalBusinessEventAuditListener implements LocalBusinessEventListener {
    private final AuditRecorder auditRecorder;

    public LocalBusinessEventAuditListener(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Override
    public void onEvent(LocalBusinessEvent event) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("eventType", event.type().name());
        detail.put("contextType", event.contextType());
        detail.put("moduleCode", event.moduleCode());
        detail.put("occurredTime", event.occurredTime());
        // 审计仅记录来源和身份引用；通知与自动化订阅者也应按当前权限重新读取数据。
        auditRecorder.record("local-event-" + event.type().name() + "-" + event.sourceId(),
                event.actorAccountId(), event.systemId(), event.tenantId(), event.actorMemberId(),
                "LOCAL_BUSINESS_EVENT_DELIVERED", event.sourceType(), event.sourceId(), "PUBLISHED", detail);
    }
}
