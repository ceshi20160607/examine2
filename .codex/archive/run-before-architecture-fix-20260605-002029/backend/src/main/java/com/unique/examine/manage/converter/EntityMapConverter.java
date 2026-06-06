package com.unique.examine.manage.converter;

import com.unique.examine.manage.vo.SimpleVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EntityMapConverter {
    private final ObjectMapper objectMapper;

    public SimpleVO toSimple(Object entity) {
        Map<String, Object> fields = objectMapper.convertValue(entity, new TypeReference<>() {});
        fields.remove("passwordHash");
        fields.remove("secretDigest");
        SimpleVO vo = new SimpleVO();
        vo.setFields(fields);
        return vo;
    }
}
