package com.unique.unexamine.system.manage;

import com.unique.unexamine.platform.manage.AccountSessionService;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理员建系统以及成员进入有权限系统的页面接口。 */
@RestController
@RequestMapping("/api/systems")
public class SystemManageController {
    private final AccountSessionService sessions;
    private final SystemManageService systems;

    public SystemManageController(AccountSessionService sessions, SystemManageService systems) {
        this.sessions = sessions;
        this.systems = systems;
    }

    /** 从页面创建业务系统和对应的主租户。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SystemView create(@Valid @RequestBody CreateSystemRequest body, HttpServletRequest request) {
        Long accountId = sessions.requireAccount(request).getId();
        return SystemView.from(systems.create(accountId, body.code(), body.name(), body.description()), accountId);
    }

    /** 列出登录账号当前可进入的系统。 */
    @GetMapping
    public List<SystemView> list(HttpServletRequest request) {
        Long accountId = sessions.requireAccount(request).getId();
        return systems.list(accountId).stream().map(system -> SystemView.from(system, accountId)).toList();
    }

    /** 根据成员关系进入系统并读取其基本信息。 */
    @GetMapping("/{id}")
    public SystemView enter(@PathVariable Long id, HttpServletRequest request) {
        Long accountId = sessions.requireAccount(request).getId();
        return SystemView.from(systems.enter(accountId, id), accountId);
    }

    /** 首版系统创建输入；编码是稳定标识，不支持后续改名时修改。 */
    public record CreateSystemRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,39}", message = "编码需为3到40位小写字母、数字或下划线，并以字母开头") String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description) {}

    /** 仅公开页面必需的系统信息，不返回表实体的内部字段。 */
    public record SystemView(Long id, String code, String name, String description, String status, boolean owner) {
        public static SystemView from(SystemDefinition system, Long accountId) {
            return new SystemView(system.getId(), system.getCode(), system.getName(), system.getDescription(), system.getStatus(),
                    accountId.equals(system.getOwnerAccountId()));
        }
    }
}
