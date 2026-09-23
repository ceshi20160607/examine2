package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 重现页面十进制字符串与流程数值条件的协议冲突，内部往返不能丢类型或精度。 */
class FlowJsonStorageTest {
    @Test
    void keepsInternalDecimalNumericAndExactWhileApplicationResponseRemainsString() throws Exception {
        ObjectMapper application = new ObjectMapper();
        SimpleModule api = new SimpleModule();
        api.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                generator.writeString(value.toPlainString());
            }
        });
        application.registerModule(api);
        ObjectMapper storage = FlowJsonStorage.mapper(application);
        BigDecimal amount = new BigDecimal("123456789012345678901234567890.1234567890123456789");
        Map<String, Object> source = Map.of("amount", amount, "enabled", false);
        assertTrue(application.valueToTree(source).path("amount").isTextual());
        assertTrue(storage.valueToTree(source).path("amount").isNumber());
        Map<?, ?> restored = storage.readValue(storage.writeValueAsString(source), Map.class);
        assertEquals(amount, restored.get("amount"));
        assertEquals(false, restored.get("enabled"));
        assertTrue(storage.valueToTree(restored).path("amount").isNumber());
        assertEquals("invalid-number", storage.valueToTree(Map.of("amount", "invalid-number")).path("amount").asText());
    }
}
