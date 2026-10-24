package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.sys.manage.dto.SystemManageDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 导出使用独立菜单，并且显式请求无导出许可的列时明确拒绝。 */
class ModuleRecordExportPermissionTest {
    private final ModulePermissionService permissions=mock(ModulePermissionService.class);
    private final ModuleConfigService modules=mock(ModuleConfigService.class);
    private final ModuleSceneService scenes=mock(ModuleSceneService.class);
    private final ModuleColumnService columns=mock(ModuleColumnService.class);
    private final IUnModuleRecordService records=mock(IUnModuleRecordService.class);
    private final ModuleShareService shares=mock(ModuleShareService.class);
    private final ModuleRecordListService service=new ModuleRecordListService(permissions,modules,scenes,columns,
            mock(ModuleFieldService.class),records,mock(IUnModuleRecordDataService.class),new ObjectMapper(),shares,
            mock(ModuleRelationFieldService.class));

    private UnModuleField field(long id,String code) {
        var row=new UnModuleField(); row.setId(id); row.setFieldKey(code); row.setFieldName(code);
        row.setFieldType((short)1); return row;
    }

    private void setup() {
        var context=new SystemManageDto.SystemContext(1L,2L,10L,11L,12L,false,false);
        var policy=new PermissionPolicy(true,true,Set.of(),true,Set.of(),false,Set.of(),false,Set.of("name"));
        var module=new UnModule(); module.setId(3L);
        var settings=new ModuleMenuDto.Settings("导出","EXPORT","LIST_TOOLBAR",List.of(),List.of(),null,List.of());
        var definition=new ModuleMenuDto.Definition(module,null,null,null,settings);
        when(permissions.require("token",1L,2L,3L,"EXPORT"))
                .thenReturn(new ModulePermissionService.Access(context,10L,policy,definition));
        when(modules.activeModule(1L,3L,2L)).thenReturn(module);
        when(modules.currentFields(3L)).thenReturn(List.of(field(11L,"name"),field(12L,"secret")));
        when(scenes.resolve(eq(3L),isNull(),any())).thenReturn(
                new ModuleSceneDto.Settings(List.of(),List.of(),null,true));
        when(columns.effective(eq(3L),any())).thenReturn(Map.of());
        when(shares.applyRecordScope(any(QueryWrapper.class),any(),eq("ALL"))).thenReturn(true);
        when(records.page(any(Page.class),any(QueryWrapper.class))).thenReturn(new Page<UnModuleRecord>(1,500));
    }

    @Test void defaultExportOmitsFieldsWithoutExportPermission() {
        setup();
        var query=new ModuleRecordSearchBO(); query.setLimit(500);

        var result=service.export("token",1L,2L,3L,query);

        assertEquals(List.of("name"),result.columns().stream().map(ModuleRecordListDto.Column::code).toList());
        verify(permissions).require("token",1L,2L,3L,"EXPORT");
        verify(permissions,never()).require("token",1L,2L,3L,"LIST");
    }

    @Test void explicitForbiddenColumnFailsBeforeRecordQuery() {
        setup();
        var query=new ModuleRecordSearchBO(); query.setLimit(500); query.setColumnFieldIds(List.of(12L));

        assertThrows(ResponseStatusException.class,()->service.export("token",1L,2L,3L,query));
        verify(records,never()).page(any(Page.class),any(QueryWrapper.class));
    }
}
