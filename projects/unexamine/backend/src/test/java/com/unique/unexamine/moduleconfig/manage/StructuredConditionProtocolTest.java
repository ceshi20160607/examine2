package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 覆盖会扩大审批/共享范围的类型边界，不重复已有通用条件树测试。 */
class StructuredConditionProtocolTest {
    private final ObjectMapper json = new ObjectMapper();
    private final StructuredConditionProtocol protocol = new StructuredConditionProtocol(json, new StructuredRuleEvaluator());

    private JsonNode node(String text) throws Exception { return json.readTree(text); }

    private List<StructuredConditionProtocol.Field> fields(String type) throws Exception {
        return List.of(new StructuredConditionProtocol.Field("value", "业务字段", type,
                node("{\"options\":[{\"value\":\"a\"},{\"value\":\"b\"}]}")));
    }

    @Test
    void sameInstantUsesBusinessTimezoneForEqualityInequalityAndOrdering() throws Exception {
        var field = fields("DATETIME");
        var actual = node("{\"value\":\"2026-09-18T10:00:00.000\"}");
        assertTrue(protocol.matches(node("{\"field\":\"value\",\"operator\":\"EQ\",\"value\":\"2026-09-18T02:00:00Z\"}"), actual, field));
        assertFalse(protocol.matches(node("{\"field\":\"value\",\"operator\":\"NE\",\"value\":\"2026-09-18T02:00:00Z\"}"), actual, field));
        assertTrue(protocol.matches(node("{\"field\":\"value\",\"operator\":\"GT\",\"value\":\"2026-09-18T01:59:59.999Z\"}"), actual, field));
        assertTrue(protocol.matches(node("{\"field\":\"value\",\"operator\":\"IN\",\"value\":[\"2026-09-18T10:00\"]}"), actual, field));
    }

    @Test
    void invalidActualOrExcessPrecisionNeverBecomesTrueInequality() throws Exception {
        var condition = node("{\"field\":\"value\",\"operator\":\"NE\",\"value\":\"2026-09-18T10:00\"}");
        assertThrows(DomainException.class, () -> protocol.matches(condition, node("{\"value\":\"tomorrow\"}"), fields("DATETIME")));
        assertThrows(DomainException.class, () -> protocol.matches(condition, node("{\"value\":\"2026-09-18T10:00:00.000001\"}"), fields("DATETIME")));
        assertThrows(DomainException.class, () -> protocol.normalize(node("{\"field\":\"value\",\"operator\":\"EQ\",\"value\":\"2026-02-30\"}"), fields("DATE"), "TEST_INVALID"));
    }

    @Test
    void dateShapedTextIsNeverGuessedAsDatetime() throws Exception {
        assertTrue(protocol.matches(node("{\"field\":\"value\",\"operator\":\"NE\",\"value\":\"2026-09-18T02:00:00Z\"}"),
                node("{\"value\":\"2026-09-18T10:00:00+08:00\"}"), fields("TEXT")));
    }

    @Test
    void optionConstantsUseCurrentDictionaryWhileHistoricalActualValuesRemainReadable() throws Exception {
        assertThrows(DomainException.class, () -> protocol.normalize(node("{\"field\":\"value\",\"operator\":\"EQ\",\"value\":\"retired\"}"), fields("SINGLE_SELECT"), "TEST_INVALID"));
        assertTrue(protocol.matches(node("{\"field\":\"value\",\"operator\":\"NE\",\"value\":\"a\"}"), node("{\"value\":\"retired\"}"), fields("SINGLE_SELECT")));
        assertTrue(protocol.matches(node("{\"field\":\"value\",\"operator\":\"EQ\",\"value\":[\"a\",\"b\"]}"), node("{\"value\":[\"b\",\"a\",\"a\"]}"), fields("MULTI_SELECT")));
        assertTrue(protocol.matches(node("{\"field\":\"value\",\"operator\":\"CONTAINS\",\"value\":\"b\"}"), node("{\"value\":[\"a\",\"b\"]}"), fields("MULTI_SELECT")));
    }

    @Test
    void typedConstantsAndUnavailableFieldsCannotPublish() throws Exception {
        assertThrows(DomainException.class, () -> protocol.normalize(node("{\"field\":\"value\",\"operator\":\"EQ\",\"value\":\"12\"}"), fields("NUMBER"), "TEST_INVALID"));
        assertThrows(DomainException.class, () -> protocol.normalize(node("{\"field\":\"value\",\"operator\":\"EQ\",\"value\":1}"), fields("BOOLEAN"), "TEST_INVALID"));
        assertThrows(DomainException.class, () -> protocol.normalize(node("{\"field\":\"value\",\"operator\":\"IN\",\"value\":[]}"), fields("TEXT"), "TEST_INVALID"));
        assertThrows(DomainException.class, () -> protocol.normalize(node("{\"field\":\"hidden\",\"operator\":\"NE\",\"value\":\"a\"}"), fields("TEXT"), "TEST_INVALID"));
    }
}
