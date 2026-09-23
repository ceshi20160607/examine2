package com.unique.unexamine.foundation.manage.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.shared.manage.security.RuntimeSecretCipher;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 真实请求形状的安全边界：元数据仍能关联，原提交不变，普通流程上下文不能消费明文。 */
class BusinessActionValueProtectionTest {
    private final ObjectMapper json = new ObjectMapper();
    private final BusinessActionValueProtection protection = new BusinessActionValueProtection(json,
            new RuntimeSecretCipher("synthetic-test-master-key-for-runtime-only"));

    @Test
    void protectsBothRequestShapesWithoutConfusingFieldAndContainerNames() throws Exception {
        ObjectNode configuration = (ObjectNode) json.readTree("""
                {"fields":[{"code":"credential","fieldType":"SECRET"},{"code":"fields","fieldType":"SECRET"}]}
                """);
        ObjectNode event = (ObjectNode) json.readTree("""
                {"action":{"actor":{"systemId":54,"accountId":69},"dataTenantId":77,"moduleId":47,
                 "configVersionId":74,"actionCode":"UPDATE","recordId":154,"traceId":"synthetic-trace",
                 "payload":{"fields":{"credential":"synthetic-plain-value","fields":"another-plain-value","subject":"正常主题"},
                            "values":{"credential":"manual-plain-value"}}},"mode":"AFTER_APPROVAL","flowInstanceId":23}
                """);
        ObjectNode saved = protection.protect(event, protection.secretFields(configuration));
        assertFalse(saved.toString().contains("plain-value"));
        assertTrue(event.toString().contains("synthetic-plain-value"));
        assertEquals(154, saved.path("action").path("recordId").asInt());
        ObjectNode restored = protection.restoreForExecution(saved);
        assertEquals(event.path("action"), restored.path("action"));
        assertEquals("正常主题", saved.path("action").path("payload").path("fields").path("subject").asText());
        JsonNodeAssertions.assertFlowRedaction(json.valueToTree(protection.flowInput(saved)));
        saved.withObject("action").put("dataTenantId", 57);
        assertThrows(DomainException.class, () -> protection.restoreForExecution(saved));
    }

    /** 流程快照不能得到明文或可恢复密文，包含与请求容器同名的合法业务字段。 */
    private static class JsonNodeAssertions {
        static void assertFlowRedaction(com.fasterxml.jackson.databind.JsonNode flowAction) {
            assertFalse(flowAction.toString().contains("plain-value"));
            assertFalse(flowAction.toString().contains("runtime-secret:v1:"));
            assertEquals("***", flowAction.path("payload").path("fields").path("fields").asText());
            assertEquals("***", flowAction.path("payload").path("values").path("credential").asText());
        }
    }

    @Test
    void missingAndExplicitNullValuesRemainDifferent() throws Exception {
        ObjectNode configuration = (ObjectNode) json.readTree("{\"fields\":[{\"code\":\"credential\",\"fieldType\":\"SECRET\"}]}");
        ObjectNode event = (ObjectNode) json.readTree("{\"action\":{\"payload\":{\"fields\":{\"credential\":null},\"values\":{}}}}");
        ObjectNode saved = protection.protect(event, protection.secretFields(configuration));
        assertTrue(saved.path("action").path("payload").path("fields").path("credential").isNull());
        assertFalse(saved.path("action").path("payload").path("values").has("credential"));
        assertEquals(event.path("action"), protection.restoreForExecution(saved).path("action"));
    }
}
