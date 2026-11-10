package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.sys.manage.dto.SystemManageDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto;
import com.unique.unexamine.sys.module.manage.dto.ModulePermissionDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.core.module.ModuleDeleteApprovalStarter;
import com.unique.unexamine.core.module.ModuleRecordProcessContext;
import com.unique.unexamine.core.module.ModuleRecordProcessHandler;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.base.entity.UnSysMenu;
import com.unique.unexamine.sys.base.service.*;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.*;
import com.unique.unexamine.sys.module.base.service.*;
import com.unique.unexamine.sys.module.manage.controller.ModuleRecordController;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证批量删除逐条使用动作负责人范围，避免前几条删除后才发现后续记录无权。 */
class ModuleRecordBatchDeleteTest {
    private final SystemManageService systems=mock(SystemManageService.class);
    private final ModuleConfigService config=mock(ModuleConfigService.class);
    private final IUnModuleRecordService records=mock(IUnModuleRecordService.class);
    private final IUnModuleLogService logs=mock(IUnModuleLogService.class);
    private final ModulePermissionService permissions=mock(ModulePermissionService.class);
    private final IUnPlatAccountService accounts=mock(IUnPlatAccountService.class);
    private final ModuleRecordService service=new ModuleRecordService(systems,config,
            mock(IUnSysSystemService.class),mock(IUnSysMemberService.class),mock(IUnSysTenantMemberService.class),
            records,mock(IUnModuleRecordDataService.class),logs,new ObjectMapper(),permissions,
            mock(ModuleFieldService.class),mock(ModuleMenuService.class),mock(IUnModuleRelationService.class),
            accounts,mock(ModuleShareService.class),mock(ModuleFileBatchService.class),mock(ModuleRelationFieldService.class),
            mock(IUnModuleSequenceService.class));

    private void access(boolean allData) {
        var context=new SystemManageDto.SystemContext(1L,2L,10L,11L,12L,false,false);
        var policy=new PermissionPolicy(true,allData,Set.of(101L),true,Set.of(),true,Set.of(),true,Set.of());
        var module=new UnModule(); module.setId(3L);
        var menu=new UnSysMenu(); menu.setId(4L); menu.setName("删除");
        var definition=new ModuleMenuDto.Definition(module,menu,null,null,null);
        when(permissions.require("token",1L,2L,3L,"DELETE"))
                .thenReturn(new ModulePermissionService.Access(context,10L,policy,definition));
        when(config.activeModule(1L,3L,2L)).thenReturn(module);
    }

    private UnModuleRecord record(long id,long owner) {
        var row=new UnModuleRecord(); row.setId(id); row.setSystemId(1L); row.setTenantId(2L);
        row.setModuleId(3L); row.setOwnerUserId(owner); row.setLockVersion(0L);
        when(records.queryById(id)).thenReturn(row);
        return row;
    }

    @Test void rejectsWholeSelectionBeforeWritingWhenOneRecordIsOutsideOwnerScope() {
        access(false); record(21L,101L); record(22L,102L);

        assertThrows(BaseException.class,()->service.deleteByIds("token",1L,2L,3L,
                new ModuleRecordDto.DeleteRecordsRequest(List.of(21L,22L))));

        verify(records,never()).update(any(UpdateWrapper.class));
        verify(logs,never()).addOrUpdate(any());
    }

    @Test void httpBatchDeleteWritesSeparateHistoryForAuthorizedRecords() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(),""),UnModuleRecord.class);
        access(true);
        var first=record(21L,101L); var second=record(22L,102L);
        when(records.update(any(UpdateWrapper.class))).thenReturn(true);
        var actor=new UnPlatAccount(); actor.setDisplayName("操作人");
        when(accounts.queryById(10L)).thenReturn(actor);

        MockMvcBuilders.standaloneSetup(new ModuleRecordController(service,mock(ModuleRecordListService.class),
                mock(ModuleRelationFieldService.class)))
                .build().perform(post("/api/systems/1/modules/3/records/batch-delete")
                        .header("Authorization","token").header("X-Tenant-Id","2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[21,22]}"))
                .andExpect(status().isOk());

        assertNotNull(first.getDeletedTime());
        assertEquals(first.getDeletedTime(),second.getDeletedTime());
        verify(records,times(2)).update(any(UpdateWrapper.class));
        var history=ArgumentCaptor.forClass(UnModuleLog.class);
        verify(logs,times(2)).addOrUpdate(history.capture());
        assertEquals(Set.of(21L,22L),history.getAllValues().stream().map(UnModuleLog::getRecordId)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(history.getAllValues().stream().allMatch(log->"RECORD_DELETE".equals(log.getActionCode())));
    }

    @Test void approvalKeepsRecordUntilCompletedAndThenDeletesOnce() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(),""),UnModuleRecord.class);
        access(true);
        var row=record(21L,101L); row.setName("待删除记录");
        when(config.currentFields(3L)).thenReturn(List.of());
        when(records.getOne(any(),eq(false))).thenReturn(row);
        when(records.update(any(UpdateWrapper.class))).thenReturn(true);
        var actor=new UnPlatAccount(); actor.setDisplayName("申请人");
        when(accounts.queryById(10L)).thenReturn(actor);
        AtomicReference<Map<String,Object>> requested=new AtomicReference<>();
        class Starter implements ModuleRecordProcessHandler, ModuleDeleteApprovalStarter {
            @Override public void afterSave(ModuleRecordProcessContext context) {}
            @Override public boolean startDeleteApproval(ModuleRecordProcessContext context,Map<String,Object> metadata) {
                requested.set(new HashMap<>(metadata)); return true;
            }
        }
        ReflectionTestUtils.setField(service,"processHandlers",List.<ModuleRecordProcessHandler>of(new Starter()));

        service.deleteByIds("token",1L,2L,3L,new ModuleRecordDto.DeleteRecordsRequest(List.of(21L)));
        assertNull(row.getDeletedTime());
        verify(records,never()).update(any(UpdateWrapper.class));
        assertEquals(10L,requested.get().get("deleteRequesterAccountId"));

        Map<String,Object> metadata=new HashMap<>(requested.get()); metadata.put("deleteRequest",true);
        var rejected=new ExamineCompletionContext(1L,2L,3L,21L,12L,30L,41L,"REJECTED",Map.of(),metadata);
        new ModuleDeleteAfterExamineHandler(service).afterCompletion(rejected);
        assertNull(row.getDeletedTime());
        var approved=new ExamineCompletionContext(1L,2L,3L,21L,12L,30L,42L,"COMPLETED",Map.of(),metadata);
        new ModuleDeleteAfterExamineHandler(service).afterCompletion(approved);
        assertNotNull(row.getDeletedTime());
        verify(records,times(1)).update(any(UpdateWrapper.class));
        var history=ArgumentCaptor.forClass(UnModuleLog.class);
        verify(logs,times(2)).addOrUpdate(history.capture());
        assertEquals(List.of("RECORD_DELETE_REQUEST","RECORD_DELETE"),
                history.getAllValues().stream().map(UnModuleLog::getActionCode).toList());
    }
}
