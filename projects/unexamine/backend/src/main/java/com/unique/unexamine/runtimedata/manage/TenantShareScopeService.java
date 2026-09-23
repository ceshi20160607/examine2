package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.moduleconfig.manage.StructuredRuleEvaluator;
import com.unique.unexamine.moduleconfig.manage.DictionaryConfigurationService;
import com.unique.unexamine.runtimedata.base.entity.*;
import com.unique.unexamine.runtimedata.base.mapper.BusinessRecordValueMapper;
import com.unique.unexamine.runtimedata.base.mapper.BusinessRecordParticipantMapper;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** 记录与动态范围共享的唯一范围判断；目录、查询和动作均使用真实来源数据。 */
@Service
public class TenantShareScopeService {
    // 尚未贯通的关联、级联等类型不开放手输 ID；其条件能力保留在后续字段链中。
    public static final Set<String> CONDITION_TYPES = Set.of("TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL",
            "URL", "ID_CARD", "NUMBER", "MONEY", "DATE", "DATETIME", "BOOLEAN", "SINGLE_SELECT", "MULTI_SELECT", "STATUS");
    private final ObjectMapper json;
    private final StructuredRuleEvaluator evaluator;
    private final ModulePublicationService publications;
    private final BusinessRecordValueMapper values;
    private final BusinessRecordParticipantMapper participants;
    private final DictionaryConfigurationService dictionaries;

    public TenantShareScopeService(ObjectMapper json, StructuredRuleEvaluator evaluator,
            ModulePublicationService publications, BusinessRecordValueMapper values,
            BusinessRecordParticipantMapper participants, DictionaryConfigurationService dictionaries) {
        this.json = json; this.evaluator = evaluator; this.publications = publications;
        this.values = values; this.participants = participants;
        this.dictionaries = dictionaries;
    }

    /** 不依赖容器时区；预定、过期和撤销授权均不能提供当前访问。 */
    public String status(BizTenantShare share) {
        if (share == null) return "MISSING";
        if (!"ACTIVE".equals(share.getStatus())) return share.getStatus();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (share.getExpiresAt() != null && !share.getExpiresAt().isAfter(now)) return "EXPIRED";
        if (share.getStartTime() != null && share.getStartTime().isAfter(now)) return "SCHEDULED";
        return "ACTIVE";
    }

    /** 原身份只切换配置归属；不能借用来源成员、角色或平台权限。 */
    public RuntimeModuleConfiguration sourcePublication(AuthenticatedContext actor, BizTenantShare share, String moduleCode) {
        AuthenticatedContext source = new AuthenticatedContext(actor.sessionId(), actor.accountId(), actor.platformId(),
                actor.systemId(), share.getSourceTenantId(), actor.memberId(), actor.tenantMemberId(), actor.username(),
                actor.displayName(), actor.mfaLevel(), actor.roleIds(), actor.permissions(), actor.dataScopes());
        return publications.published(source, moduleCode);
    }

    /** 目录可保留暂时没有匹配记录的范围；字段删除或类型变化使原范围失效而不是放宽。 */
    public boolean validRange(AuthenticatedContext actor, BizTenantShare share, String moduleCode) {
        if (!"DATA_RANGE".equals(share.getScopeType())) return false;
        try {
            JsonNode scope = read(share.getScopeJson());
            RuntimeModuleConfiguration publication = sourcePublication(actor, share, moduleCode);
            if (!share.getModuleId().equals(publication.moduleId()) || !scope.path("sourceShareScope").isArray()
                    || scope.path("sourceShareScope").isEmpty() || !scope.path("fieldBindings").isObject()) return false;
            Map<String, String> current = new HashMap<>();
            publication.configuration().path("fields").forEach(f -> {
                if (CONDITION_TYPES.contains(f.path("fieldType").asText())) current.put(f.path("code").asText(), f.path("id").asText() + ":" + f.path("fieldType").asText());
            });
            var bindings = scope.path("fieldBindings").fields();
            while (bindings.hasNext()) {
                var binding = bindings.next();
                if (!Objects.equals(current.get(binding.getKey()), binding.getValue().asText())) return false;
            }
            List<TenantRangeShareModels.ConditionField> typed = new ArrayList<>();
            for (JsonNode field : publication.configuration().path("fields")) {
                String code = field.path("code").asText(), type = field.path("fieldType").asText();
                if (!scope.path("fieldBindings").has(code)) continue;
                var config = read(field.path("configJson").asText("{}")).deepCopy();
                if (Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS").contains(type) && field.path("dictionaryId").isIntegralNumber()) {
                    // 同类型改绑其他字典也改变条件语义，原授权须由来源明确重新授予。
                    JsonNode dictionaryBinding = scope.path("dictionaryBindings").path(code);
                    if (!dictionaryBinding.isIntegralNumber() || field.path("dictionaryId").longValue() != dictionaryBinding.longValue()) return false;
                    var options = ((ObjectNode) config).putArray("options");
                    AuthenticatedContext source = sourceContext(actor, share.getSourceTenantId());
                    dictionaries.publishedDisplayItems(source, field.path("dictionaryId").longValue()).stream()
                            .filter(item -> "ACTIVE".equals(item.path("status").asText())).forEach(item -> options.addObject()
                                .put("value", item.path("code").asText()).put("label", item.path("label").asText()));
                }
                typed.add(new TenantRangeShareModels.ConditionField(code, field.path("name").asText(), type, config.toString()));
            }
            Set<String> bound = new HashSet<>(); scope.path("fieldBindings").fieldNames().forEachRemaining(bound::add);
            if (!evaluator.inspectConditions(scope.path("condition"), bound).isEmpty()) return false;
            validate(scope.path("condition"), typed);return true;
        } catch (RuntimeException e) { return false; }
    }

    /** 每个动作必须选择确实匹配该记录的授权，不能合并其他范围的动作后扩大授权。 */
    public boolean matches(AuthenticatedContext actor, BizTenantShare share, BusinessRecord record, String moduleCode) {
        return matches(actor, share, record, moduleCode, false);
    }

    /** 写动作使用当前锁定读，条件值和参与者不能使用事务早先的旧快照。 */
    public boolean matches(AuthenticatedContext actor, BizTenantShare share, BusinessRecord record, String moduleCode, boolean currentRead) {
        if (!"ACTIVE".equals(status(share)) || !Objects.equals(actor.systemId(), share.getSystemId())
                || !Objects.equals(record.getSystemId(), share.getSystemId())
                || !Objects.equals(record.getTenantId(), share.getSourceTenantId())
                || !Objects.equals(record.getModuleId(), share.getModuleId())) return false;
        if ("RECORD".equals(share.getScopeType())) return Objects.equals(record.getId(), share.getRecordId());
        if (!validRange(actor, share, moduleCode)) return false;
        JsonNode scope = read(share.getScopeJson());
        if (!withinSourceScope(scope.path("sourceShareScope"), record, currentRead)) return false;
        Set<String> codes = new HashSet<>(); scope.path("fieldBindings").fieldNames().forEachRemaining(codes::add);
        Map<String, JsonNode> actual = new HashMap<>();
        if (!codes.isEmpty()) values.selectList(Wrappers.<BusinessRecordValue>lambdaQuery()
                .eq(BusinessRecordValue::getRecordId, record.getId()).in(BusinessRecordValue::getFieldCode, codes).last(currentRead, "FOR SHARE"))
                .forEach(value -> { if (!"SECRET".equals(value.getValueType())) actual.put(value.getFieldCode(), stored(value)); });
        // 比较结果含实际值，不落日志、不返回目标租户。
        return evaluator.evaluateConditions(scope.path("condition"), json.valueToTree(actual), new ArrayList<>());
    }

    private boolean withinSourceScope(JsonNode clauses, BusinessRecord record, boolean currentRead) {
        for (JsonNode clause : clauses) {
            String type = clause.path("type").asText();
            if ("ALL".equals(type)) return true;
            Long value = switch (type) {
                case "OWNER" -> record.getOwnerMemberId();
                case "CREATOR" -> record.getCreatedByMemberId();
                case "DEPARTMENT" -> record.getDepartmentId();
                default -> null;
            };
            if (value != null) for (JsonNode id : clause.path("ids")) if (id.isIntegralNumber() && value.equals(id.longValue())) return true;
            if ("PARTICIPATED".equals(type) && clause.path("memberId").isIntegralNumber()
                    && !participants.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                        .eq(BusinessRecordParticipant::getSystemId, record.getSystemId())
                        .eq(BusinessRecordParticipant::getTenantId, record.getTenantId())
                        .eq(BusinessRecordParticipant::getRecordId, record.getId())
                        .eq(BusinessRecordParticipant::getSystemMemberId, clause.path("memberId").longValue()).last(currentRead, "FOR SHARE")).isEmpty()) return true;
        }
        return false;
    }

    /** 与运行字段存储协议保持同型，不读取密钥，更不以默认值补齐缺失条件。 */
    private JsonNode stored(BusinessRecordValue value) {
        if (value.getValueText() != null) return TextNode.valueOf(value.getValueText());
        if (value.getValueNumber() != null) return DecimalNode.valueOf(value.getValueNumber());
        if (value.getValueDate() != null) return TextNode.valueOf(value.getValueDate().toString());
        if (value.getValueDatetime() != null) return TextNode.valueOf(value.getValueDatetime().toString());
        if (value.getValueBoolean() != null) return BooleanNode.valueOf(value.getValueBoolean());
        if (value.getValueJson() != null) return read(value.getValueJson());
        return NullNode.instance;
    }

    /** 授权条件结构与现有业务条件树相同，额外检查运算符和比较值的字段类型。 */
    public void validate(JsonNode condition, List<TenantRangeShareModels.ConditionField> fields) {
        Map<String, TenantRangeShareModels.ConditionField> indexed = new HashMap<>();fields.forEach(f -> indexed.put(f.code(), f));
        List<String> issues = evaluator.inspectConditions(condition, indexed.keySet());
        if (!condition.isObject() || !condition.has("conditions")) issues.add("范围需要配置条件组");
        if (!issues.isEmpty()) throw invalid(String.join("；", issues));
        validateValues(condition, indexed);
    }

    private void validateValues(JsonNode node, Map<String, TenantRangeShareModels.ConditionField> fields) {
        if (node.has("conditions")) { node.path("conditions").forEach(child -> validateValues(child, fields)); return; }
        var field = fields.get(node.path("field").asText()); String type = field.fieldType(); String op = node.path("operator").asText();
        if (Set.of("EMPTY", "NOT_EMPTY").contains(op)) return;
        if (Set.of("GT", "GTE", "LT", "LTE").contains(op) && !Set.of("NUMBER", "MONEY", "DATE", "DATETIME").contains(type)) throw invalid(field.name() + "不支持大小比较");
        if ("CONTAINS".equals(op) && !Set.of("TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "MULTI_SELECT").contains(type)) throw invalid(field.name() + "不支持包含比较");
        JsonNode value = node.path("value");
        if ("IN".equals(op)) {
            if ("MULTI_SELECT".equals(type) || value.isEmpty()) throw invalid(field.name() + "的比较值无效");
            value.forEach(v -> validateValue(field, v, false));
        } else validateValue(field, value, "CONTAINS".equals(op) && "MULTI_SELECT".equals(type));
    }

    private void validateValue(TenantRangeShareModels.ConditionField field, JsonNode value, boolean multiContains) {
        String type = field.fieldType(); boolean valid;
        if (Set.of("NUMBER", "MONEY").contains(type)) valid = value.isNumber();
        else if ("BOOLEAN".equals(type)) valid = value.isBoolean();
        else if ("MULTI_SELECT".equals(type) && !multiContains) valid = value.isArray() && java.util.stream.StreamSupport.stream(value.spliterator(), false).allMatch(JsonNode::isTextual);
        else valid = value.isTextual();
        try {
            if ("DATE".equals(type)) LocalDate.parse(value.asText());
            if ("DATETIME".equals(type)) { try { OffsetDateTime.parse(value.asText()); } catch (java.time.format.DateTimeParseException e) { LocalDateTime.parse(value.asText()); } }
        } catch (java.time.format.DateTimeParseException e) { valid = false; }
        if (!valid) throw invalid(field.name() + "的比较值类型不正确");
        if (Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS").contains(type)) {
            Set<String> allowed = new HashSet<>();read(field.configJson()).path("options").forEach(o -> allowed.add(o.path("value").asText()));
            List<JsonNode> selected = value.isArray() ? java.util.stream.StreamSupport.stream(value.spliterator(), false).toList() : List.of(value);
            if (selected.stream().anyMatch(v -> !allowed.contains(v.asText()))) throw invalid(field.name() + "的选项已失效，请重新选择");
        }
    }

    /** 只绑定条件实际引用的字段，新增无关字段不会使历史授权失效。 */
    public ObjectNode bindings(JsonNode condition, RuntimeModuleConfiguration publication) {
        Set<String> codes = new HashSet<>(); referenced(condition, codes);ObjectNode result = json.createObjectNode();
        publication.configuration().path("fields").forEach(f -> { if (codes.contains(f.path("code").asText())) result.put(f.path("code").asText(), f.path("id").asText() + ":" + f.path("fieldType").asText()); });
        return result;
    }

    /** 字典身份与字段身份一起冻结，选项合法性在每次当前范围判定时复核。 */
    public ObjectNode dictionaryBindings(JsonNode condition, RuntimeModuleConfiguration publication) {
        Set<String> codes = new HashSet<>();referenced(condition, codes);ObjectNode result = json.createObjectNode();
        publication.configuration().path("fields").forEach(f -> {
            if (codes.contains(f.path("code").asText()) && Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS").contains(f.path("fieldType").asText())
                    && f.path("dictionaryId").isIntegralNumber()) result.set(f.path("code").asText(), f.path("dictionaryId"));
        });return result;
    }

    /** 时间条件按可信字段类型规范化，避免省略零秒、毫秒或偏移导致 NE 意外放宽范围。 */
    public JsonNode normalizeCondition(JsonNode condition, List<TenantRangeShareModels.ConditionField> fields) {
        JsonNode copy = condition.deepCopy();Map<String, String> types = new HashMap<>();fields.forEach(f -> types.put(f.code(), f.fieldType()));
        normalizeNode(copy, types);return copy;
    }
    private void normalizeNode(JsonNode node, Map<String, String> types) {
        if (node.has("conditions")) { node.path("conditions").forEach(child -> normalizeNode(child, types));return; }
        String type = types.get(node.path("field").asText());
        if (!Set.of("DATE", "DATETIME").contains(type) || !node.has("value")) return;
        JsonNode value = node.path("value");
        if (value.isArray()) for (int i = 0; i < value.size(); i++) ((ArrayNode) value).set(i, normalizeTime(type, value.get(i)));
        else ((ObjectNode) node).set("value", normalizeTime(type, value));
    }
    private JsonNode normalizeTime(String type, JsonNode value) {
        if ("DATE".equals(type)) return TextNode.valueOf(LocalDate.parse(value.asText()).toString());
        LocalDateTime time;
        try { time = OffsetDateTime.parse(value.asText()).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime(); }
        catch (java.time.format.DateTimeParseException e) { time = LocalDateTime.parse(value.asText()); }
        if (time.getNano() % 1_000_000 != 0) throw invalid("范围时间比较值的精度最多为毫秒");
        return TextNode.valueOf(time.toString());
    }

    private AuthenticatedContext sourceContext(AuthenticatedContext actor, Long tenantId) {
        return new AuthenticatedContext(actor.sessionId(), actor.accountId(), actor.platformId(), actor.systemId(), tenantId,
                actor.memberId(), actor.tenantMemberId(), actor.username(), actor.displayName(), actor.mfaLevel(), actor.roleIds(),
                actor.permissions(), actor.dataScopes());
    }
    private void referenced(JsonNode node, Set<String> result) {
        if (node.has("conditions")) node.path("conditions").forEach(child -> referenced(child, result));
        else result.add(node.path("field").asText());
    }
    public JsonNode read(String source) {
        try { return json.readTree(source == null ? "{}" : source); }
        catch (Exception e) { throw invalid("共享条件配置损坏，请由来源管理员检查"); }
    }
    private DomainException invalid(String message) { return new DomainException("SHARE_CONDITION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY); }
}
