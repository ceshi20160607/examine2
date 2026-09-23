package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 条件使用当前发布字段及字典；普通编辑目录另取动作/渠道字段权限交集。 */
@Component
public class PublishedConditionCatalog {
    private final ObjectMapper json;
    private final DictionaryConfigurationService dictionaries;
    private final ChannelFieldPolicyResolver policies;

    public PublishedConditionCatalog(ObjectMapper json, DictionaryConfigurationService dictionaries,
                                     ChannelFieldPolicyResolver policies) {
        this.json = json;
        this.dictionaries = dictionaries;
        this.policies = policies;
    }

    /** 内部判定依据来源已发布配置，不以目标用户显示裁剪改变来源业务条件。 */
    public List<StructuredConditionProtocol.Field> fields(AuthenticatedContext context, RuntimeModuleConfiguration publication) {
        List<StructuredConditionProtocol.Field> result = new ArrayList<>();
        for (JsonNode field : publication.configuration().path("fields")) {
            String type = field.path("fieldType").asText();
            if (!"ACTIVE".equals(field.path("status").asText()) || !StructuredConditionProtocol.TYPES.contains(type)) continue;
            JsonNode configured;
            try { configured = json.readTree(field.path("configJson").asText("{}")); }
            catch (Exception invalid) { throw new DomainException("CONDITION_FIELD_CONFIG_INVALID", "条件字段配置无法读取", HttpStatus.UNPROCESSABLE_ENTITY); }
            ObjectNode safe = json.createObjectNode();
            for (String key : List.of("options", "placeholder", "maxLength")) if (configured.has(key)) safe.set(key, configured.get(key));
            // 手动选项与绑定字典遵守同一生效状态，目录不能把已停用项再次当作可发布条件常量。
            if (java.util.Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS").contains(type) && safe.has("options")) {
                var options = json.createArrayNode();
                for (JsonNode option : safe.path("options")) {
                    if (!"ACTIVE".equals(option.path("status").asText("ACTIVE")) || option.path("disabled").asBoolean(false)) continue;
                    options.addObject().put("value", option.path("value").asText(option.path("code").asText()))
                            .put("label", option.path("label").asText());
                }
                safe.set("options", options);
            }
            if (java.util.Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS").contains(type)
                    && field.path("dictionaryId").isIntegralNumber()) {
                var options = safe.putArray("options");
                dictionaries.publishedDisplayItems(context, field.path("dictionaryId").longValue()).stream()
                        .filter(item -> "ACTIVE".equals(item.path("status").asText()))
                        .forEach(item -> options.addObject().put("value", item.path("code").asText()).put("label", item.path("label").asText()));
            }
            result.add(new StructuredConditionProtocol.Field(field.path("code").asText(), field.path("name").asText(), type, safe));
        }
        return List.copyOf(result);
    }

    /** 仅公开当前可读、无脱敏的字段及安全选项；不包含默认值、密码或记录样本。 */
    public List<StructuredConditionProtocol.Field> readableFields(AuthenticatedContext context,
            String moduleCode, RuntimeModuleConfiguration publication, String action, List<String> channels) {
        var fields = fields(context, publication);
        var access = policies.resolveIntersection(context, moduleCode, action, channels,
                fields.stream().map(StructuredConditionProtocol.Field::code).toList());
        return fields.stream().filter(field -> {
            var decision = access.get(field.code());
            return decision != null && decision.readable() && decision.maskStrategy() == null;
        }).toList();
    }
}
