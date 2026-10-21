package com.unique.unexamine.web.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** 统一定义后端接口文档的标题、版本和用途，便于前端和验收人员阅读。 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Unexamine 后端接口",
        version = "0.1.0",
        description = "可配置模块、流程、审批、工作协作和数据展示基础接口。"
))
public class OpenApiConfig {
}
