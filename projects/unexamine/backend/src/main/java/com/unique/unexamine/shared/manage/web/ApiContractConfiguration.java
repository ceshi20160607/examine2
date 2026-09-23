package com.unique.unexamine.shared.manage.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
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
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

@Configuration
public class ApiContractConfiguration {
    /** 当前 API 和数据库 DATETIME 的统一墙上时间约定，启动时也用于 JVM 默认时区。 */
    static final ZoneId API_ZONE = ZoneId.of("Asia/Shanghai");

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
            builder.deserializerByType(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                @Override
                public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                        return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
                    }
                    String value = parser.getText().trim();
                    try {
                        TemporalAccessor time = DateTimeFormatter.ISO_DATE_TIME.parse(value);
                        // 无时区沿用系统墙上时间；带时区先换算同一时刻，再存入数据库 DATETIME。
                        if (time.isSupported(ChronoField.OFFSET_SECONDS)) {
                            return OffsetDateTime.from(time).atZoneSameInstant(API_ZONE).toLocalDateTime();
                        }
                        return LocalDateTime.from(time);
                    } catch (DateTimeException exception) {
                        return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, value, "日期时间格式不正确");
                    }
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
