package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.moduleconfig.manage.StructuredRuleEvaluator;
import com.unique.unexamine.runtimedata.base.entity.BizRecordIndex;
import com.unique.unexamine.runtimedata.base.service.BizRecordIndexBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeRuleIndexServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final BizRecordIndexBaseService indexes = mock(BizRecordIndexBaseService.class);
    private final RuntimeRuleIndexService service = new RuntimeRuleIndexService(
            new StructuredRuleEvaluator(), indexes, json);

    @Test
    void publishedRuleRejectionCarriesRuleIdentityWithoutFieldValues() throws Exception {
        var configuration = new RuntimeModuleConfiguration(1L, 2L, 1, 1, json.readTree("""
                {"rules":[{"id":17,"code":"amount_limit","name":"金额上限","triggerEvent":"CREATE",
                "messageTemplate":"金额不能超过上限","expressionText":"{\\\"mode\\\":\\\"ALL\\\",\\\"conditions\\\":[{\\\"field\\\":\\\"amount\\\",\\\"operator\\\":\\\"GT\\\",\\\"value\\\":1000}],\\\"effect\\\":{\\\"type\\\":\\\"BLOCK\\\"}}"}]}
                """));

        DomainException failure = catchThrowableOfType(
                () -> service.validate(configuration, "CREATE", Map.of("amount", LongNode.valueOf(1500))),
                DomainException.class);

        assertThat(failure.code()).isEqualTo("BUSINESS_RULE_REJECTED");
        assertThat(failure.details()).containsEntry("anomalyType", "BUSINESS_RULE")
                .containsEntry("ruleId", 17L).containsEntry("ruleCode", "amount_limit")
                .containsEntry("ruleName", "金额上限");
        assertThat(failure.details().toString()).doesNotContain("1500");
    }

    @Test
    void uniqueRejectionCarriesIndexIdentityWithoutUniqueValue() throws Exception {
        when(indexes.selectList(any())).thenReturn(List.of(new BizRecordIndex()));
        var configuration = new RuntimeModuleConfiguration(1L, 2L, 1, 1, json.readTree("""
                {"rules":[],"fields":[{"id":11,"code":"phone"}],
                "queryIndexes":[{"id":19,"code":"phone_unique","name":"手机号唯一","uniqueIndex":true}],
                "queryIndexFields":[{"queryIndexId":19,"fieldId":11,"sortOrder":0}]}
                """));
        AuthenticatedContext actor = new AuthenticatedContext(null, 1L, 2L, 3L, 4L, 5L, 6L,
                "operator", "业务人员", "PASSWORD", List.of(), List.of(), Map.of());

        DomainException failure = catchThrowableOfType(
                () -> service.validateCreatePreview(actor, configuration,
                        Map.of("phone", TextNode.valueOf("13800138000"))), DomainException.class);

        assertThat(failure.code()).isEqualTo("UNIQUE_RULE_VIOLATION");
        assertThat(failure.details()).containsEntry("anomalyType", "UNIQUE_RULE")
                .containsEntry("ruleId", 19L).containsEntry("ruleCode", "phone_unique")
                .containsEntry("ruleName", "手机号唯一");
        assertThat(failure.details().toString()).doesNotContain("13800138000");
    }
}
