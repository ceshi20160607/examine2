package com.unique.unexamine.audit.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.entity.AuditFieldChange;
import com.unique.unexamine.audit.base.service.AuditFieldChangeBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 审计列表和详情共用的敏感业务值保护，包含历史事件的读时遮罩。 */
@Service
public class AuditEventRedactor {
    private final AuditFieldChangeBaseService fieldChanges;
    private final PermissionChecker permissionChecker;

    public AuditEventRedactor(AuditFieldChangeBaseService fieldChanges, PermissionChecker permissionChecker) {
        this.fieldChanges = fieldChanges;
        this.permissionChecker = permissionChecker;
    }

    /** 无敏感审计权限时，事件快照不能绕过字段变更的遮罩返回原始业务值。 */
    public void redact(AuthenticatedContext context, List<AuditEvent> events) {
        boolean sensitiveVisible = context.systemId() == null
                ? permissionChecker.allows(context, "PLATFORM", "AUDIT", "VIEW_SENSITIVE")
                : permissionChecker.allows(context, "AUDIT", "EVENT", "VIEW_SENSITIVE");
        if (events.isEmpty() || sensitiveVisible) {
            return;
        }
        List<Long> eventIds = events.stream().map(AuditEvent::getId).toList();
        Set<Long> sensitiveEvents = fieldChanges.selectList(Wrappers.<AuditFieldChange>lambdaQuery()
                        .in(AuditFieldChange::getAuditEventId, eventIds)
                        .eq(AuditFieldChange::getSensitivity, "SENSITIVE"))
                .stream().map(AuditFieldChange::getAuditEventId).collect(Collectors.toSet());
        for (AuditEvent event : events) {
            if (sensitiveEvents.contains(event.getId())) {
                event.setDetailJson("{\"masked\":true}");
                event.setPermissionSnapshot(null);
            }
        }
    }
}
