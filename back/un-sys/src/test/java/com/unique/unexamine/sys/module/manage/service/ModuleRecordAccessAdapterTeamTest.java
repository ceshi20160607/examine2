package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.sys.manage.dto.SystemManageDto;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleTeamer;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleTeamerService;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import org.springframework.test.util.ReflectionTestUtils;

/** 审批等跨模块入口也必须逐条检查团队授权，不能凭同模块其他记录的团队资格越权。 */
class ModuleRecordAccessAdapterTeamTest {
    @Test
    void teamAccessIsLimitedToTheGrantedRecord() {
        var permissions=mock(ModulePermissionService.class);
        var records=mock(IUnModuleRecordService.class);
        var adapter=new ModuleRecordAccessAdapter(permissions,records,
                mock(ModuleRecordService.class),mock(SystemManageService.class));
        var context=new SystemManageDto.SystemContext(1L,2L,10L,11L,12L,false,false);
        var policy=new PermissionPolicy(true,false,Set.of(),false,Set.of(),false,Set.of(),false,Set.of());
        var access=new ModulePermissionService.Access(context,10L,policy,null);
        when(permissions.require("token",1L,2L,3L,"DETAIL")).thenReturn(access);
        UnModuleRecord granted=record(21L);
        UnModuleRecord other=record(22L);
        when(records.getById(21L)).thenReturn(granted);
        when(records.getById(22L)).thenReturn(other);
        when(permissions.teamAllows(access,granted)).thenReturn(true);

        assertEquals(10L,adapter.require("token",1L,2L,3L,21L).accountId());
        assertThrows(BaseException.class,()->adapter.require("token",1L,2L,3L,22L));
    }

    @Test
    void viewMemberCannotUseEditButEditMemberCan() {
        var permissions=mock(ModulePermissionService.class,CALLS_REAL_METHODS);
        var teamers=mock(IUnModuleTeamerService.class);
        ReflectionTestUtils.setField(permissions,"teamers",teamers);
        var context=new SystemManageDto.SystemContext(1L,2L,10L,11L,12L,false,false);
        var policy=new PermissionPolicy(true,false,Set.of(),false,Set.of(),false,Set.of(),false,Set.of());
        var module=new UnModule(); module.setId(3L);
        var row=record(21L);
        var teamer=new UnModuleTeamer(); teamer.setAccessLevel("VIEW");
        when(teamers.getOne(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(false))).thenReturn(teamer);
        var detail=new ModuleMenuDto.Definition(module,null,null,null,
                new ModuleMenuDto.Settings("详情","DETAIL","ROW",null,null,null,null));
        var edit=new ModuleMenuDto.Definition(module,null,null,null,
                new ModuleMenuDto.Settings("编辑","EDIT","ROW",null,null,null,null));
        assertEquals(true,permissions.teamAllows(new ModulePermissionService.Access(context,10L,policy,detail),row));
        assertEquals(false,permissions.teamAllows(new ModulePermissionService.Access(context,10L,policy,edit),row));
        teamer.setAccessLevel("EDIT");
        assertEquals(true,permissions.teamAllows(new ModulePermissionService.Access(context,10L,policy,edit),row));
        var delete=new ModuleMenuDto.Definition(module,null,null,null,
                new ModuleMenuDto.Settings("删除","DELETE","ROW",null,null,null,null));
        var custom=new ModuleMenuDto.Definition(module,null,null,null,
                new ModuleMenuDto.Settings("变更状态","UPDATE_FIELDS","ROW",null,null,null,null));
        assertEquals(false,permissions.teamAllows(new ModulePermissionService.Access(context,10L,policy,delete),row));
        assertEquals(false,permissions.teamAllows(new ModulePermissionService.Access(context,10L,policy,custom),row));
    }

    private UnModuleRecord record(Long id) {
        var record=new UnModuleRecord(); record.setId(id); record.setSystemId(1L);
        record.setTenantId(2L); record.setModuleId(3L); record.setOwnerUserId(99L);
        return record;
    }
}
