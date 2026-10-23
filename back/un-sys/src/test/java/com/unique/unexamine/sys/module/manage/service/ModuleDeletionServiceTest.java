package com.unique.unexamine.sys.module.manage.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.core.file.base.entity.UnCommonFile;
import com.unique.unexamine.core.file.base.service.IUnCommonFileService;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.file.FileStorage;
import com.unique.unexamine.sys.base.service.*;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.*;
import com.unique.unexamine.sys.module.base.service.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** 删除必须明确选择；完整备份受权限约束，导出后的数据变化不能被旧备份放行。 */
class ModuleDeletionServiceTest {
    private final SystemManageService systems=mock(SystemManageService.class);
    private final ModuleConfigService config=mock(ModuleConfigService.class);
    private final ModulePermissionService permissions=mock(ModulePermissionService.class);
    private final IUnModuleService modules=mock(IUnModuleService.class);
    private final IUnModuleRecordService records=mock(IUnModuleRecordService.class);
    private final IUnModuleRecordDataService values=mock(IUnModuleRecordDataService.class);
    private final IUnModuleFieldService fields=mock(IUnModuleFieldService.class);
    private final IUnModuleExchangeJobService jobs=mock(IUnModuleExchangeJobService.class);
    private final IUnCommonFileService files=mock(IUnCommonFileService.class);
    private final FileStorage storage=mock(FileStorage.class);
    private final ObjectMapper json=new ObjectMapper();
    private final SystemManageService.SystemContext context=new SystemManageService.SystemContext(1L,2L,10L,11L,12L,true,true);
    private final UnModule module=new UnModule();
    private final UnModuleRecord record=new UnModuleRecord();
    private final ModuleDeletionService service=new ModuleDeletionService(systems,config,permissions,modules,records,
            values,fields,jobs,files,mock(IUnModuleLogService.class),mock(IUnSysLogOptService.class),
            mock(IUnSysMenuService.class),mock(IUnModuleMenuService.class),mock(IUnSysSystemConfigService.class),
            mock(ConfigurationCache.class),storage,json);

    private void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(),""),UnModuleRecordData.class);
        module.setId(3L); module.setSystemId(1L); module.setTenantId(2L); module.setName("商机");
        record.setId(21L); record.setSystemId(1L); record.setModuleId(3L); record.setTenantId(2L);
        record.setOwnerUserId(10L); record.setName("原商机");
        when(systems.requireAdminContext("token",1L,2L)).thenReturn(context);
        when(systems.accountId("token",1L)).thenReturn(10L);
        when(modules.queryById(3L)).thenReturn(module);
        when(records.count(any(Wrapper.class))).thenReturn(1L);
        var field=new UnModuleField(); field.setId(31L); field.setFieldCode("name");
        when(fields.list(any(Wrapper.class))).thenReturn(List.of(field));
        var policy=new PermissionPolicy(true,true,Set.of(),true,Set.of(),true,Set.of(),true,Set.of());
        when(permissions.require("token",1L,2L,3L,"EXPORT"))
                .thenReturn(new ModulePermissionService.Access(context,10L,policy,null));
    }

    @Test void dataRequiresExplicitChoiceBeforeAnyDeletion() {
        setup();
        var failure=assertThrows(ResponseStatusException.class,()->service.delete("token",1L,2L,3L,null));
        assertEquals(409,failure.getStatusCode().value());
        verify(modules,never()).removeById(anyLong()); verify(values,never()).remove(any(Wrapper.class));
    }

    @Test void inheritedModuleCannotBeDeletedFromAnotherTenant() {
        setup(); module.setTenantId(99L);
        var failure=assertThrows(ResponseStatusException.class,()->service.check("token",1L,2L,3L));
        assertEquals(403,failure.getStatusCode().value()); verifyNoInteractions(storage);
    }

    @Test void changedDataAfterCompleteBackupBlocksDeletion() throws Exception {
        setup();
        when(records.list(any(Wrapper.class))).thenReturn(List.of(record),List.of(),List.of(record),List.of());
        final byte[][] backupBytes={null};
        when(storage.store(any(InputStream.class))).thenAnswer(invocation->{
            backupBytes[0]=invocation.<InputStream>getArgument(0).readAllBytes();
            return new FileStorage.StoredFile("backup",backupBytes[0].length,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(backupBytes[0])));
        });
        when(storage.open("backup")).thenAnswer(invocation->new ByteArrayInputStream(backupBytes[0]));
        when(files.addOrUpdate(any())).thenAnswer(invocation->{
            UnCommonFile file=invocation.getArgument(0); file.setId(41L); when(files.getById(41L)).thenReturn(file); return file;
        });
        when(jobs.addOrUpdate(any())).thenAnswer(invocation->{
            UnModuleExchangeJob job=invocation.getArgument(0); job.setId(51L); when(jobs.getById(51L)).thenReturn(job); return job;
        });
        TransactionSynchronizationManager.initSynchronization();
        try {
            var backup=service.export("token",1L,2L,3L);
            assertEquals(1,backup.recordCount());
            assertEquals("原商机",json.readTree(backupBytes[0]).path("records").get(0).path("record").path("name").asText());
            record.setName("导出后的新商机");
            var failure=assertThrows(ResponseStatusException.class,()->service.delete("token",1L,2L,3L,
                    new ModuleDeletionService.DeleteRequest(ModuleDeletionService.Choice.EXPORTED,51L)));
            assertEquals(409,failure.getStatusCode().value());
            verify(modules,never()).removeById(anyLong()); verify(values,never()).remove(any(Wrapper.class));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void missingFieldExportPermissionDoesNotCreatePartialBackup() throws Exception {
        setup();
        when(records.list(any(Wrapper.class))).thenReturn(List.of(record));
        var denied=new PermissionPolicy(true,true,Set.of(),true,Set.of(),true,Set.of(),false,Set.of());
        when(permissions.require("token",1L,2L,3L,"EXPORT"))
                .thenReturn(new ModulePermissionService.Access(context,10L,denied,null));
        var failure=assertThrows(ResponseStatusException.class,()->service.export("token",1L,2L,3L));
        assertEquals(403,failure.getStatusCode().value()); verifyNoInteractions(storage,files,jobs);
    }
}
