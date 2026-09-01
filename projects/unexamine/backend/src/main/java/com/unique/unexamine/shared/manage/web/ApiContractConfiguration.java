package com.unique.unexamine.shared.manage.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class ApiContractConfiguration {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    OpenAPI productApiDocumentation() {
        return new OpenAPI().info(new Info()
                .title("Unexamine 产品接口")
                .version("v1")
                .description("平台与系统内部产品接口。应用授权访问使用独立路径和认证链。"));
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer apiScalarSerialization() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override
                public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider serializers)
                        throws IOException {
                    generator.writeString(value.atZone(API_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
            });
            builder.serializerByType(BigDecimal.class, new JsonSerializer<BigDecimal>() {
                @Override
                public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider serializers)
                        throws IOException {
                    generator.writeString(value.toPlainString());
                }
            });
        };
    }
}
