package com.unique.unexamine.sys.manage.dto;

import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 系统组织目录的 HTTP 请求与返回模型，不暴露成员和部门实体。 */
public final class SystemDirectoryDto {
    private SystemDirectoryDto() {}

    /** 保存租户部门；部门编码在同一租户内保持稳定。 */
    public record DepartmentRequest(
            @Schema(description = "已有部门主键") Long id,
            @Schema(description = "同租户上级部门主键") Long parentId,
            @NotBlank @Size(max = 80) @Schema(description = "租户内部门编码") String code,
            @NotBlank @Size(max = 100) @Schema(description = "部门名称") String name,
            @Schema(description = "同级排序") Integer sortNo) {}

    /** 部门树节点。 */
    public record DepartmentView(Long id, Long parentId, String code, String name,
                                 Integer sortNo, String status, LocalDateTime updateTime) {}

    /** 成员查询条件；分页沿用 core 的 SearchBO。 */
    public static class MemberSearch extends SearchBO {
        @Schema(description = "部门主键") private Long departmentId;
        @Schema(description = "成员状态") private String status;

        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 当前租户成员及其组织关系。 */
    public record MemberView(Long tenantMemberId, Long memberId, Long accountId,
                             String displayName, String employeeNo, Long departmentId,
                             Long supervisorAccountId, Boolean isAdmin, String status,
                             LocalDateTime joinTime) {}

    /** 当前租户角色的配置摘要。 */
    public record RoleView(Long id, String code, String name, String description,
                           String status, LocalDateTime updateTime) {}
}
