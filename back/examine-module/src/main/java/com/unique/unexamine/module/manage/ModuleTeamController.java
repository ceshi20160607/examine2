package com.unique.unexamine.module.manage;

import com.unique.unexamine.module.base.entity.UnModuleTeamer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模块团队 HTTP 入口：只绑定参数并委托业务服务。 */
@Tag(name = "模块团队", description = "模块记录团队成员。")
@RestController
@RequestMapping("/api/modules/{moduleId}")
public class ModuleTeamController {
    private final ModuleTeamService service;

    public ModuleTeamController(ModuleTeamService service) {
        this.service = service;
    }

    /** 查询当前成员可读取的记录团队。 */
    @Operation(summary = "查询团队成员（模块团队）")
    @GetMapping("/records/{recordId}/teamers")
    public List<UnModuleTeamer> teamers(@PathVariable long moduleId, @PathVariable long recordId,
                                        @RequestParam long tenantId, HttpServletRequest request) {
        return service.teamers(moduleId, recordId, tenantId, request);
    }

    /** 向记录团队添加租户成员。 */
    @Operation(summary = "添加团队成员（模块团队）")
    @PostMapping("/records/{recordId}/teamers")
    public UnModuleTeamer addTeamer(@PathVariable long moduleId, @PathVariable long recordId,
                                    @RequestParam long tenantId, @Valid @RequestBody TeamerForm body,
                                    HttpServletRequest request) {
        return service.addTeamer(moduleId, recordId, tenantId, body, request);
    }

    /** 移除当前租户记录的团队成员。 */
    @Operation(summary = "移除团队成员（模块团队）")
    @DeleteMapping("/records/{recordId}/teamers/{id}")
    public void deleteTeamer(@PathVariable long moduleId, @PathVariable long recordId, @PathVariable long id,
                             @RequestParam long tenantId, HttpServletRequest request) {
        service.deleteTeamer(moduleId, recordId, id, tenantId, request);
    }

    /** 团队成员与记录内角色输入。 */
    public record TeamerForm(@NotNull Long memberId, @NotBlank String roleCode) { }
}
