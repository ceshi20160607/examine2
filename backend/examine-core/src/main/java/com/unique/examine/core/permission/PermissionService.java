package com.unique.examine.core.permission;

/**
 * 统一权限服务。
 */
public interface PermissionService {

    /**
     * 获取当前请求主体的有效权限。
     *
     * @return 有效权限
     */
    EffectivePermissionVO currentPermission();

    /**
     * 判定权限。
     *
     * @param check 权限判定入参
     * @return 判定结果
     */
    PermissionDecision decide(PermissionCheck check);

    /**
     * 要求具备操作权限。
     *
     * @param operationCode 操作编码
     */
    void requireOperation(String operationCode);

    /**
     * 要求具备 OpenAPI scope 权限。
     *
     * @param openApiScope OpenAPI scope 编码
     */
    void requireOpenApiScope(String openApiScope);

    /**
     * 要求字段可写。
     *
     * @param fieldCode 字段编码
     */
    void requireFieldWritable(String fieldCode);

    /**
     * 要求符合数据范围。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源 ID
     * @param targetMemberId 目标成员 ID
     */
    void requireDataScope(String resourceType, String resourceId, String targetMemberId);
}
