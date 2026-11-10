package com.unique.unexamine.sys.module.manage.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleScence;
import com.unique.unexamine.sys.module.base.entity.UnModuleScenceConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleScenceConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleScenceService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 场景运行态读取的损坏配置必须转换为统一业务异常。 */
class ModuleSceneServiceTest {
    private final SystemManageService systems = mock(SystemManageService.class);
    private final ModulePermissionService permissions = mock(ModulePermissionService.class);
    private final ModuleConfigService modules = mock(ModuleConfigService.class);
    private final ModuleFieldService fieldRules = mock(ModuleFieldService.class);
    private final IUnModuleScenceService scenes = mock(IUnModuleScenceService.class);
    private final IUnModuleScenceConfigService configurations = mock(IUnModuleScenceConfigService.class);
    private final ModuleSceneService service = new ModuleSceneService(systems, permissions, modules, fieldRules,
            scenes, configurations, new ObjectMapper());

    @Test
    void nullJsonCollectionsAreReportedAsConfigurationConflict() {
        UnModuleScence scene = new UnModuleScence();
        scene.setId(1L);
        scene.setModuleId(10L);
        scene.setTenantId(20L);
        scene.setStatus("ACTIVE");
        when(scenes.queryById(1L)).thenReturn(scene);
        UnModuleScenceConfig row = new UnModuleScenceConfig();
        row.setFilterJson("null");
        row.setColumnJson("[]");
        row.setSortJson("{\"fieldId\":0,\"descending\":true}");
        when(configurations.getOne(any())).thenReturn(row);
        when(modules.currentFields(10L)).thenReturn(List.of());
        PermissionPolicy policy = new PermissionPolicy(true, true, java.util.Set.of(), true, java.util.Set.of(),
                true, java.util.Set.of(), true, java.util.Set.of());
        ModulePermissionService.Access access = new ModulePermissionService.Access(
                new SystemContext(10L, 20L, 30L, 40L, 50L, false, false), 30L, policy, null);

        assertThrows(BaseException.class, () -> service.resolve(10L, 1L, access));
    }
}
