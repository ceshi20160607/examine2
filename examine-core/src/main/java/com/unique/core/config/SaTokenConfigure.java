package com.unique.core.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.MybatisMapWrapperFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.unique.core.context.BaseConst;
import com.unique.core.utils.BaseUtil;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author UNIQUE
 * @create 2023-03-25
 * @verson 1.0.0
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    // 注册拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        System.out.println("--------- flag 1");
        registry.addInterceptor(new SaInterceptor(handle -> {
                    System.out.println("--------- flag 2，请求进入了拦截器，访问的 path 是：" + SaHolder.getRequest().getRequestPath());
                    System.out.println("----"+StpUtil.getLoginId());
                    StpUtil.checkLogin();  // 登录校验，只有会话登录后才能通过这句代码
                    System.out.println("----"+StpUtil.getLoginId());
                }))
//                .addPathPatterns("/user/**")
                .addPathPatterns("/**")
                .excludePathPatterns("/moduleAdmin/doLogin","/moduleAdmin/doLoginTest","/error");
    }


    /**
     * long类型数据统一处理转换为string
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return jacksonObjectMapperBuilder -> {
            jacksonObjectMapperBuilder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            jacksonObjectMapperBuilder.serializerByType(Long.class, ToStringSerializer.instance);
            jacksonObjectMapperBuilder.deserializerByType(Date.class, new DateDeSerializer());
            //localDate类型的序列化
            jacksonObjectMapperBuilder.serializers(new LocalDateTimeSerializer(DatePattern.NORM_DATETIME_FORMATTER), new LocalDateSerializer(DatePattern.NORM_DATE_FORMATTER));
            //localDate类型的反序列化
            jacksonObjectMapperBuilder.deserializers(new LocalDateTimeDeserializer(DatePattern.NORM_DATETIME_FORMATTER), new LocalDateDeserializer(DatePattern.NORM_DATE_FORMATTER));
        };
    }

    /**
     * 对objectMapper增加一些时间类型的处理
     *
     * @return objectMapper
     */
    @Bean
    public ObjectMapper buildObjectMapper() {
        ObjectMapper objectMapper = new JsonMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.setDateFormat(new SimpleDateFormat(DatePattern.NORM_DATETIME_PATTERN));
        objectMapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DatePattern.NORM_DATETIME_FORMATTER));
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DatePattern.NORM_DATE_FORMATTER));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DatePattern.NORM_DATETIME_FORMATTER));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DatePattern.NORM_DATE_FORMATTER));
        javaTimeModule.addDeserializer(Date.class, new DateDeSerializer());
        javaTimeModule.addSerializer(Long.class, ToStringSerializer.instance);
        javaTimeModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(javaTimeModule);
        return objectMapper;
    }

    @Bean
    public PaginationInnerInterceptor paginationInterceptor() {
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(BaseConst.QUERY_MAX_SIZE * 100);
        paginationInterceptor.setOptimizeJoin(true);
        return paginationInterceptor;
    }

    @Bean
    public ConfigurationCustomizer configurationCustomizer() {
        return i -> i.setObjectWrapperFactory(new MybatisMapWrapperFactory());
    }

    @Bean
    @Primary
    public IdentifierGenerator idGenerator() {
        return entity -> BaseUtil.getNextId();
    }

    /**
     * 自定义long序列化，因为默认情况下long类型都序列化成string,在需要long类型数据时使用
     * 将注解加到bean属性上即可
     * <p>
     * \@JsonSerialize(using = WebConfig.NumberSerializer.class)
     */
    public static class NumberSerializer extends JsonSerializer<Long> {

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeNumber(value);
        }
    }

    /**
     * date反序列化
     */
    public static class DateDeSerializer extends JsonDeserializer<Date> {

        @Override
        public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (StrUtil.isNotEmpty(text)) {
                return DateUtil.parse(text);
            }
            return null;
        }
    }
}
