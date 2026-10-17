package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.moduleconfig.base.entity.BusinessModule;
import com.unique.unexamine.moduleconfig.base.entity.ModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ModuleMenu;
import com.unique.unexamine.moduleconfig.base.entity.ModuleVersion;
import com.unique.unexamine.platform.manage.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 系统后台基础模块配置及运行态发布读取。 */
@RestController
@RequestMapping("/api/systems/{systemId}")
public class ModuleManageController {
    private final AccountSessionService sessions;
    private final ModuleManageService modules;

    public ModuleManageController(AccountSessionService sessions, ModuleManageService modules) {
        this.sessions = sessions;
        this.modules = modules;
    }

    /** 创建运行态顶栏的模块组。 */
    @PostMapping("/module-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupView createGroup(@PathVariable Long systemId, @Valid @RequestBody GroupRequest body, HttpServletRequest request) {
        return GroupView.from(modules.createGroup(accountId(request), systemId, body.code(), body.name()));
    }

    /** 读取当前系统的模块组草稿。 */
    @GetMapping("/module-groups")
    public List<GroupView> groups(@PathVariable Long systemId, HttpServletRequest request) {
        return modules.groups(accountId(request), systemId).stream().map(GroupView::from).toList();
    }

    /** 在模块组下创建模块与默认列表菜单。 */
    @PostMapping("/modules")
    @ResponseStatus(HttpStatus.CREATED)
    public ModuleView createModule(@PathVariable Long systemId, @Valid @RequestBody ModuleRequest body, HttpServletRequest request) {
        return ModuleView.from(modules.createModule(accountId(request), systemId, body.groupId(), body.code(), body.name()));
    }

    /** 读取模块组下的配置列表。 */
    @GetMapping("/modules")
    public List<ModuleView> modules(@PathVariable Long systemId, @RequestParam Long groupId, HttpServletRequest request) {
        return modules.modules(accountId(request), systemId, groupId).stream().map(ModuleView::from).toList();
    }

    /** 添加模块草稿字段；基础字段类型在服务端验证。 */
    @PostMapping("/modules/{moduleId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public FieldView addField(@PathVariable Long systemId, @PathVariable Long moduleId,
                              @Valid @RequestBody FieldRequest body, HttpServletRequest request) {
        return FieldView.from(modules.addField(accountId(request), systemId, moduleId, body.key(), body.label(), body.type(),
                body.required(), body.searchable(), body.optionsJson()));
    }

    /** 读取字段草稿。 */
    @GetMapping("/modules/{moduleId}/fields")
    public List<FieldView> fields(@PathVariable Long systemId, @PathVariable Long moduleId, HttpServletRequest request) {
        return modules.fields(accountId(request), systemId, moduleId).stream().map(FieldView::from).toList();
    }

    /** 修改草稿字段名称，用于验证已发布快照不随草稿变化。 */
    @PutMapping("/modules/{moduleId}/fields/{fieldId}")
    public FieldView renameField(@PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long fieldId,
                                 @Valid @RequestBody RenameFieldRequest body, HttpServletRequest request) {
        return FieldView.from(modules.renameField(accountId(request), systemId, moduleId, fieldId, body.label()));
    }

    /** 读取模块动作草稿；动作开关需重新发布才进入运行态。 */
    @GetMapping("/modules/{moduleId}/actions")
    public List<ActionView> actions(@PathVariable Long systemId, @PathVariable Long moduleId, HttpServletRequest request) {
        return modules.actions(accountId(request), systemId, moduleId).stream().map(ActionView::from).toList();
    }

    /** 修改现有页面动作的启用状态。 */
    @PutMapping("/modules/{moduleId}/actions/{code}")
    public ActionView setAction(@PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable String code,
                                @Valid @RequestBody ActionRequest body, HttpServletRequest request) {
        return ActionView.from(modules.setAction(accountId(request), systemId, moduleId, code, body.enabled()));
    }

    /** 读取默认列表菜单草稿。 */
    @GetMapping("/modules/{moduleId}/menu")
    public MenuView menu(@PathVariable Long systemId, @PathVariable Long moduleId, HttpServletRequest request) {
        return MenuView.from(modules.menu(accountId(request), systemId, moduleId));
    }

    /** 修改菜单名称，发布后才改变导航显示。 */
    @PutMapping("/modules/{moduleId}/menu")
    public MenuView renameMenu(@PathVariable Long systemId, @PathVariable Long moduleId,
                               @Valid @RequestBody RenameMenuRequest body, HttpServletRequest request) {
        return MenuView.from(modules.renameMenu(accountId(request), systemId, moduleId, body.name()));
    }

    /** 发布模块并返回新版本编号。 */
    @PostMapping("/modules/{moduleId}/publish")
    public VersionView publish(@PathVariable Long systemId, @PathVariable Long moduleId, HttpServletRequest request) {
        ModuleVersion version = modules.publish(accountId(request), systemId, moduleId);
        return new VersionView(version.getId(), version.getVersionNumber());
    }

    /** 读取当前所属人可见的已发布模块导航。 */
    @GetMapping("/runtime-navigation")
    public List<ModuleManageService.PublishedModule> navigation(@PathVariable Long systemId, HttpServletRequest request) {
        return modules.navigation(accountId(request), systemId);
    }

    /** 运行态读取单模块发布快照。 */
    @GetMapping("/modules/{moduleId}/runtime")
    public ModuleManageService.PublishedModule runtime(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                       HttpServletRequest request) {
        return modules.runtime(accountId(request), systemId, moduleId);
    }

    private Long accountId(HttpServletRequest request) { return sessions.requireAccount(request).getId(); }

    /** 模块组的稳定编码与展示名称。 */
    public record GroupRequest(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,39}") String code,
                               @NotBlank @Size(max = 120) String name) {}
    /** 新模块必须指定已有的本系统模块组。 */
    public record ModuleRequest(@NotNull Long groupId,
                                @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,39}") String code,
                                @NotBlank @Size(max = 160) String name) {}
    /** 基础字段配置；选项只适用于单选字段。 */
    public record FieldRequest(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,39}") String key,
                               @NotBlank @Size(max = 160) String label,
                               @NotBlank String type, boolean required, boolean searchable, String optionsJson) {}
    /** 字段名称草稿修改。 */
    public record RenameFieldRequest(@NotBlank @Size(max = 160) String label) {}
    /** 模块列表菜单名称长度与表字段一致。 */
    public record RenameMenuRequest(@NotBlank @Size(max = 120) String name) {}
    /** 动作草稿开关只接收明确布尔值。 */
    public record ActionRequest(@NotNull Boolean enabled) {}
    /** 页面动作草稿摘要。 */
    public record ActionView(String code, String name, boolean enabled, int sortOrder) {
        static ActionView from(ModuleAction action) {
            return new ActionView(action.getCode(), action.getName(), action.getEnabledFlag(), action.getSortOrder());
        }
    }
    /** 默认列表菜单草稿摘要。 */
    public record MenuView(String code, String name) {
        static MenuView from(ModuleMenu menu) { return new MenuView(menu.getCode(), menu.getName()); }
    }
    /** 模块组页面字段。 */
    public record GroupView(Long id, String code, String name, int sortOrder) {
        static GroupView from(ModuleGroup group) { return new GroupView(group.getId(), group.getCode(), group.getName(), group.getSortOrder()); }
    }
    /** 模块页面字段；发布版本由独立接口读取。 */
    public record ModuleView(Long id, Long groupId, String code, String name, String status, Long publishedVersionId, int draftRevision) {
        static ModuleView from(BusinessModule module) {
            return new ModuleView(module.getId(), module.getGroupId(), module.getCode(), module.getName(),
                    module.getStatus(), module.getPublishedVersionId(), module.getDraftRevision());
        }
    }
    /** 字段草稿页面字段。 */
    public record FieldView(Long id, String key, String label, String type, boolean required, boolean searchable, int sortOrder, String optionsJson) {
        static FieldView from(ModuleField field) {
            return new FieldView(field.getId(), field.getFieldKey(), field.getLabel(), field.getFieldType(),
                    field.getRequiredFlag(), field.getSearchableFlag(), field.getSortOrder(), field.getOptionsJson());
        }
    }
    /** 发布结果中的版本标识。 */
    public record VersionView(Long id, int number) {}
}
