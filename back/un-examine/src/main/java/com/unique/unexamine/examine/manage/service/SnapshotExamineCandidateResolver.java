package com.unique.unexamine.examine.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.module.ExamineCandidateResolver;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 发布快照中的固定人和指定人解析器；系统模块可追加角色、部门和上级解析器。 */
@Component
public class SnapshotExamineCandidateResolver implements ExamineCandidateResolver {
    private final IUnPlatAccountService accounts;

    public SnapshotExamineCandidateResolver(IUnPlatAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public Set<Long> resolve(JsonNode node, ExamineCandidateContext context) {
        // 系统审批的固定账号必须由系统解析器校验成员、租户和启用状态；本解析器只为平台审批保留快照账号。
        if (context != null && context.systemId() != null && context.tenantId() != null) return Set.of();
        Set<Long> result = new LinkedHashSet<>(PublishedProcessSnapshot.accountCandidates(node));
        addApprovers(result, node.get("approvers"));
        addApprovers(result, node.path("config").get("approvers"));
        // 平台审批仍须过滤已停用的统一账号，避免创建无人能办理的待办。
        result.removeIf(id -> {
            var account = accounts.queryById(id);
            return account == null || !"ACTIVE".equals(account.getStatus());
        });
        return result;
    }

    private void addApprovers(Set<Long> result, JsonNode values) {
        if (values == null || !values.isArray()) return;
        values.forEach(item -> {
            String type = item.path("type").asText(item.path("subjectType").asText("ACCOUNT"))
                    .toUpperCase(Locale.ROOT);
            if (!Set.of("ACCOUNT", "PERSON", "USER", "FIXED", "SPECIFIED").contains(type)) return;
            JsonNode id = item.get("accountId");
            if (id == null) id = item.get("id");
            if (id == null) id = item.get("subjectId");
            if (id != null && id.canConvertToLong()) result.add(id.asLong());
        });
    }
}
