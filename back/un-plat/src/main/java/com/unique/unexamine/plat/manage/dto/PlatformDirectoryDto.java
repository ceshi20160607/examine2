package com.unique.unexamine.plat.manage.dto;

import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 平台组织目录的 HTTP 模型，不把平台实体直接暴露给接口。 */
public final class PlatformDirectoryDto {
    private PlatformDirectoryDto() {}

    /** 平台部门保存请求；编码在平台范围内保持稳定。 */
    public record DepartmentRequest(
            @Schema(description = "已有部门主键") Long id,
            @Schema(description = "上级平台部门主键") Long parentId,
            @NotBlank @Size(max = 80) @Schema(description = "平台部门编码") String code,
            @NotBlank @Size(max = 100) @Schema(description = "平台部门名称") String name,
            @Schema(description = "同级排序") Integer sortNo) {}

    /** 平台部门展示模型。 */
    public record DepartmentView(Long id, Long parentId, String code, String name,
                                 Integer sortNo, String status, LocalDateTime updateTime) {}

    /** 平台成员保存请求；成员绑定统一平台账号。 */
    public record MemberRequest(
            @Schema(description = "已有平台成员主键") Long id,
            @NotNull @Schema(description = "平台账号主键") Long accountId,
            @Schema(description = "平台部门主键") Long departmentId,
            @Size(max = 80) @Schema(description = "平台员工编号") String employeeNo,
            @NotBlank @Size(max = 20) @Schema(description = "ACTIVE 或 DISABLED") String status) {}

    /** 平台成员查询条件；分页沿用 core 的 SearchBO。 */
    public static class MemberSearch extends SearchBO {
        @Schema(description = "部门主键") private Long departmentId;
        @Schema(description = "成员状态") private String status;

        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 平台成员及账号摘要。 */
    public record MemberView(Long id, Long accountId, String username, String displayName,
                             Long departmentId, String employeeNo, String status,
                             LocalDateTime updateTime) {}

    /** 平台角色摘要；具体菜单与数据授权仍使用平台角色权限表。 */
    public record RoleView(Long id, String code, String name, String status,
                           String description, LocalDateTime updateTime) {}

    /** 平台菜单保存请求；菜单编码创建后保持稳定。 */
    public record MenuRequest(
            @Schema(description = "已有菜单主键") Long id,
            @Schema(description = "上级菜单主键") Long parentId,
            @NotBlank @Size(max = 100) @Schema(description = "菜单权限编码") String code,
            @NotBlank @Size(max = 100) @Schema(description = "菜单名称") String name,
            @Size(max = 255) @Schema(description = "前端路由") String route,
            @Schema(description = "同级排序") Integer sortNo,
            @NotBlank @Size(max = 20) @Schema(description = "ACTIVE 或 DISABLED") String status) {}

    /** 平台菜单展示模型。 */
    public record MenuView(Long id, Long parentId, String code, String name, String route,
                           Integer sortNo, String status, LocalDateTime updateTime) {}

    /** 平台角色保存请求；角色编码创建后保持稳定。 */
    public record RoleRequest(
            @Schema(description = "已有角色主键") Long id,
            @NotBlank @Size(max = 80) @Schema(description = "角色编码") String code,
            @NotBlank @Size(max = 100) @Schema(description = "角色名称") String name,
            @Size(max = 500) @Schema(description = "角色说明") String description,
            @NotBlank @Size(max = 20) @Schema(description = "ACTIVE 或 DISABLED") String status) {}

    /** 成员角色绑定请求；提交后按列表替换当前有效绑定。 */
    public record MemberRolesRequest(@NotNull @Schema(description = "角色主键列表") List<Long> roleIds) {}

    /** 角色菜单动作授权请求；每个菜单动作编码在角色内保持唯一。 */
    public record RoleMenusRequest(@NotNull @Schema(description = "菜单动作授权列表") List<MenuGrant> grants) {}

    /** 单个菜单动作授权。 */
    public record MenuGrant(@NotNull Long menuId, @NotBlank @Size(max = 80) String actionCode) {}

    /** 平台角色数据范围替换请求；资源编码由平台业务定义。 */
    public record RoleDataRequest(@NotNull @Schema(description = "数据范围配置列表") List<DataGrant> grants) {}

    /** 单个平台资源的数据范围配置。 */
    public record DataGrant(@NotBlank @Size(max = 100) String resourceCode,
                            @NotBlank @Size(max = 40) String scopeCode,
                            @Schema(description = "范围补充配置 JSON 对象") Object scopeConfig) {}
}
