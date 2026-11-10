package com.unique.unexamine.app.manage.dto;

import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * 受控应用外部运行请求模型；密钥只用于本次请求验证。
 * 启用签名时，业务参数由原 values/payload 字段、__externalId 和 __businessRequest 组成；
 * __businessRequest 是完整请求体去掉 secret、signature、timestamp 后的对象，所有记录、节点和意见参数均参与签名。
 */
public final class AppExternalRuntimeDto {
    private AppExternalRuntimeDto() {}

    /** 外部应用按已授权模块 Flow 绑定发起一条业务 Flow。 */
    public record FlowStartRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部发起人标识") String externalId,
            @NotNull @Positive @Schema(description = "目标业务系统主键") Long systemId,
            @NotNull @Positive @Schema(description = "目标业务租户主键") Long tenantId,
            @NotNull @Positive @Schema(description = "业务模块主键") Long moduleId,
            @NotNull @Positive @Schema(description = "业务记录主键") Long recordId,
            @NotNull @Positive @Schema(description = "已授权的 Flow 绑定主键") Long bindingId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @Size(max = 100) @Schema(description = "Flow 启动时的业务值快照") Map<String, Object> values) {}

    /** 外部应用查询系统内 Flow 实例；数据范围仍由应用发布授权决定。 */
    public record FlowQueryRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部访问方标识") String externalId,
            @NotNull @Positive @Schema(description = "目标业务系统主键") Long systemId,
            @NotNull @Positive @Schema(description = "目标业务租户主键") Long tenantId,
            @NotNull @Positive @Schema(description = "业务模块主键") Long moduleId,
            @NotNull @Positive @Schema(description = "业务记录主键") Long recordId,
            @NotNull @Positive @Schema(description = "已授权的 Flow 绑定主键") Long bindingId,
            @NotNull @Positive @Schema(description = "Flow 实例主键") Long exampleId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature) {}

    /** 系统应用按独立 TODO 授权读取外部候选人的模块 Flow 待办。 */
    public record FlowTodoRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部处理人标识") String externalId,
            @NotNull @Positive @Schema(description = "目标业务系统主键") Long systemId,
            @NotNull @Positive @Schema(description = "目标业务租户主键") Long tenantId,
            @Positive @Schema(description = "限定 Flow 绑定主键；为空时需要资源级 TODO 授权") Long bindingId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @Valid @Schema(description = "公共分页条件") SearchBO search) {}

    /** 外部应用查询系统内审批实例；字段结果按审批快照规则返回。 */
    public record ExamineQueryRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部访问方标识") String externalId,
            @NotNull @Positive @Schema(description = "目标业务系统主键") Long systemId,
            @NotNull @Positive @Schema(description = "目标业务租户主键") Long tenantId,
            @NotNull @Positive @Schema(description = "业务模块主键") Long moduleId,
            @NotNull @Positive @Schema(description = "业务记录主键") Long recordId,
            @NotNull @Positive @Schema(description = "已授权的审批定义主键") Long examineId,
            @NotNull @Positive @Schema(description = "审批实例主键") Long exampleId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature) {}

    /** 系统应用按独立 TODO 授权读取外部审批人的当前待办。 */
    public record ExamineTodoRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部审批人标识") String externalId,
            @NotNull @Positive @Schema(description = "目标业务系统主键") Long systemId,
            @NotNull @Positive @Schema(description = "目标业务租户主键") Long tenantId,
            @Positive @Schema(description = "限定审批定义主键；为空时需要资源级 TODO 授权") Long examineId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @Valid @Schema(description = "公共分页条件") SearchBO search) {}

    /** 外部应用按已授权的平台审批定义发起独立审批。 */
    public record PlatformExamineStartRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部发起人标识") String externalId,
            @NotNull @Positive @Schema(description = "已授权的平台审批定义主键") Long examineId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @Size(max = 100) @Schema(description = "审批发起时的业务值快照") Map<String, Object> values) {}

    /** 外部应用查询不关联业务记录的平台审批实例。 */
    public record PlatformExamineQueryRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部访问方标识") String externalId,
            @NotNull @Positive @Schema(description = "已授权的平台审批定义主键") Long examineId,
            @NotNull @Positive @Schema(description = "平台审批实例主键") Long exampleId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature) {}

    /** 外部审批人查询自己的平台审批待办，单独使用 TODO 授权。 */
    public record PlatformExamineTodoRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部审批人标识") String externalId,
            @Positive @Schema(description = "限定审批定义主键；为空时需要资源级 TODO 授权") Long examineId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @Valid @Schema(description = "公共分页条件") SearchBO search) {}

    /** 外部审批人通过应用授权办理具体审批节点。 */
    public record ExamineActionRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部审批人标识") String externalId,
            @NotNull @Positive @Schema(description = "目标业务系统主键") Long systemId,
            @NotNull @Positive @Schema(description = "目标业务租户主键") Long tenantId,
            @NotNull @Positive @Schema(description = "业务模块主键") Long moduleId,
            @NotNull @Positive @Schema(description = "业务记录主键") Long recordId,
            @NotNull @Positive @Schema(description = "已授权的审批定义主键") Long examineId,
            @NotNull @Positive @Schema(description = "审批实例主键") Long exampleId,
            @NotNull @Positive @Schema(description = "审批节点主键") Long nodeId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "审批动作编码，例如 APPROVE、REJECT、TRANSFER、RETURN") String actionCode,
            @Size(max = 2000) @Schema(description = "审批意见") String opinion,
            @Size(max = 100) @Schema(description = "字段修改、转交或回退参数") Map<String, Object> payload) {}

    /** 外部应用办理模块 Flow 节点。 */
    public record FlowActionRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部处理人标识") String externalId,
            @NotNull @Positive @Schema(description = "目标业务系统主键") Long systemId,
            @NotNull @Positive @Schema(description = "目标业务租户主键") Long tenantId,
            @NotNull @Positive @Schema(description = "业务模块主键") Long moduleId,
            @NotNull @Positive @Schema(description = "业务记录主键") Long recordId,
            @NotNull @Positive @Schema(description = "已授权的 Flow 绑定主键") Long bindingId,
            @NotNull @Positive @Schema(description = "Flow 实例主键") Long exampleId,
            @NotNull @Positive @Schema(description = "Flow 待办主键") Long taskId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "Flow 动作编码，例如 EXECUTE 或 CONTINUE") String actionCode,
            @Size(max = 100) @Schema(description = "Flow 节点参数") Map<String, Object> payload) {}

    /** 外部应用按已授权平台 Flow 定义发起独立流程。 */
    public record PlatformFlowStartRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部发起人标识") String externalId,
            @NotNull @Positive @Schema(description = "已授权的平台 Flow 定义主键") Long flowId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @Size(max = 100) @Schema(description = "平台 Flow 启动时的业务值快照") Map<String, Object> values) {}

    /** 外部应用查询自己发起或参与的平台 Flow 实例。 */
    public record PlatformFlowQueryRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部访问方标识") String externalId,
            @NotNull @Positive @Schema(description = "已授权的平台 Flow 定义主键") Long flowId,
            @NotNull @Positive @Schema(description = "平台 Flow 实例主键") Long exampleId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature) {}

    /** 外部身份查询自己的平台 Flow 待办，单独使用 TODO 授权。 */
    public record PlatformFlowTodoRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部处理人标识") String externalId,
            @Positive @Schema(description = "限定流程定义主键；为空时需要资源级 TODO 授权") Long flowId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @Valid @Schema(description = "公共分页条件") SearchBO search) {}

    /** 外部候选人办理平台 Flow 待办节点。 */
    public record PlatformFlowActionRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部处理人标识") String externalId,
            @NotNull @Positive @Schema(description = "已授权的平台 Flow 定义主键") Long flowId,
            @NotNull @Positive @Schema(description = "平台 Flow 实例主键") Long exampleId,
            @NotNull @Positive @Schema(description = "平台 Flow 待办主键") Long taskId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "Flow 动作编码，例如 EXECUTE 或 CONTINUE") String actionCode,
            @Size(max = 100) @Schema(description = "平台 Flow 节点参数") Map<String, Object> payload) {}

    /** 外部应用办理不关联业务记录的平台审批节点。 */
    public record PlatformExamineActionRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "应用稳定标识") String appId,
            @NotBlank @Size(max = 512) @Schema(description = "应用当前密钥") String secret,
            @NotBlank @Size(max = 160) @Schema(description = "应用命名空间内的外部审批人标识") String externalId,
            @NotNull @Positive @Schema(description = "已授权的平台审批定义主键") Long examineId,
            @NotNull @Positive @Schema(description = "平台审批实例主键") Long exampleId,
            @NotNull @Positive @Schema(description = "审批节点主键") Long nodeId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "调用方幂等请求标识") String requestId,
            @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
            @Size(max = 256) @Schema(description = "按应用发布安全配置生成的签名") String signature,
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._:-]+")
            @Schema(description = "审批动作编码") String actionCode,
            @Size(max = 2000) @Schema(description = "审批意见") String opinion,
            @Size(max = 100) @Schema(description = "审批节点参数") Map<String, Object> payload) {}
}
