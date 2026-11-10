package com.unique.unexamine.sys.flow.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.module.ExamineCandidateResolver;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Flow 人工节点候选人解析器。
 *
 * <p>Flow 直接配置的账号与系统范围的动态规则共用同一解析入口。动态规则复用审批域
 * 的候选人解析策略，因此角色、部门和直系上级等规则不会在 Flow 中复制一套实现。</p>
 */
@Component
public class FlowCandidateResolver {
    private final List<ExamineCandidateResolver> resolvers;

    public FlowCandidateResolver(List<ExamineCandidateResolver> resolvers) {
        this.resolvers = resolvers == null ? List.of() : List.copyOf(resolvers);
    }

    /** 返回发布快照中当前作用域可解析的内部账号；结果用于创建不可变运行候选人快照。 */
    public Set<Long> resolve(JsonNode node, ExamineCandidateContext context) {
        Set<Long> result = new LinkedHashSet<>();
        try { PublishedProcessSnapshot.accountCandidates(node); }
        catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
        for (ExamineCandidateResolver resolver : resolvers) {
            Set<Long> resolved = resolver.resolve(node, context);
            if (resolved != null) result.addAll(resolved);
        }
        return result;
    }
}
