package com.unique.unexamine.sys.flow.manage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.module.ExamineCandidateResolver;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证 Flow 节点把显式账号解析为运行实例候选人快照。 */
class FlowCandidateResolverTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void resolvesExplicitAccountCandidates() throws Exception {
        var node = json.readTree("{\"nodeCode\":\"review\",\"accountIds\":[8,8,12]}");
        ExamineCandidateResolver explicit = (candidate, context) ->
                PublishedProcessSnapshot.accountCandidates(candidate);
        var resolver = new FlowCandidateResolver(List.of(explicit));
        assertEquals(List.of(8L, 12L), resolver.resolve(node,
                new ExamineCandidateContext(1L, 2L, 3L, 4L, 5L, Map.of())).stream().toList());
    }
}
