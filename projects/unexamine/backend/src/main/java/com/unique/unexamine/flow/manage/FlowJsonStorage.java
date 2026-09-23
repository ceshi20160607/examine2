package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.math.BigDecimal;

/** Flow 内部数值保留 JSON 数字类型，不能沿用页面避免精度丢失的十进制字符串输出。 */
final class FlowJsonStorage {
    private FlowJsonStorage() { }

    static ObjectMapper mapper(ObjectMapper applicationMapper) {
        SimpleModule numbers = new SimpleModule("flow-storage-numbers-v1");
        numbers.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
                generator.writeNumber(value);
            }
        });
        return applicationMapper.copy().registerModule(numbers).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
