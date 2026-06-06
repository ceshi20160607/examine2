package com.unique.examine.core.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 错误码命名空间。
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCodeNamespace {

    /** 通用参数、分页、幂等、资源不存在、并发冲突。 */
    COMMON("通用"),

    /** 登录、会话、刷新 token、账号状态。 */
    AUTH("认证"),

    /** 平台账号、平台角色、系统创建、平台配置。 */
    PLAT("平台"),

    /** 系统、租户、成员、部门、系统内角色。 */
    SYS("系统"),

    /** 应用、模块、运行记录、发布版本。 */
    MODULE("动态模块"),

    /** 字段类型、字段校验、字段权限、动态值。 */
    FIELD("字段"),

    /** 菜单、操作、数据范围、字段可见可写、导出权限。 */
    PERM("权限"),

    /** 流程模板、实例、任务、审批动作。 */
    FLOW("流程"),

    /** 文件上传、下载、预览、存储配置、引用关系。 */
    UPLOAD("上传文件"),

    /** 导出模板、导出任务、结果文件、重试。 */
    EXPORT("导出"),

    /** OpenAPI 签名、时间窗口、客户端、scope、限流、IP 白名单。 */
    OPENAPI("OpenAPI"),

    /** 操作日志、请求日志、审计查询。 */
    AUDIT("审计"),

    /** 健康检查、配置检查、版本和运行配置。 */
    OPS("运维"),

    /** 代码生成器表映射、生成任务、生成报告。 */
    GENERATOR("生成器");

    private final String description;
}
