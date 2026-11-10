package com.unique.unexamine.sys.module.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.file.base.entity.UnCommonFile;
import com.unique.unexamine.core.file.base.service.IUnCommonFileService;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.file.FileStorage;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleExchangeJob;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeJobService;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.Definition;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.Settings;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 导出文件下载必须重新检查当下的字段许可。 */
class ModuleExportJobServiceTest {
    @Test void revokedColumnCannotBeDownloaded() throws Exception {
        var systems=mock(SystemManageService.class);
        var permissions=mock(ModulePermissionService.class);
        var config=mock(ModuleConfigService.class);
        var jobs=mock(IUnModuleExchangeJobService.class);
        var files=mock(IUnCommonFileService.class);
        var storage=mock(FileStorage.class);
        var service=new ModuleExportJobService(mock(ModuleExportService.class),permissions,config,systems,
                jobs,files,mock(com.unique.unexamine.sys.base.service.IUnSysLogOptService.class),
                storage,new ObjectMapper(),mock(com.unique.unexamine.core.event.BusinessEventPublisher.class),
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                mock(PlatformTransactionManager.class),
                mock(com.unique.unexamine.sys.manage.service.SystemMessageTemplateService.class));
        var context=new SystemContext(1L,2L,3L,4L,5L,true,true);
        when(systems.context(any(),eq(1L),eq(2L))).thenReturn(context);
        var job=new UnModuleExchangeJob(); job.setId(6L); job.setSystemId(1L); job.setTenantId(2L);
        job.setModuleId(7L); job.setJobType("EXPORT"); job.setStatus("SUCCESS"); job.setResultFileId(8L);
        job.setConfigJson("{\"columns\":[{\"fieldId\":11,\"code\":\"name\"}]}");
        when(jobs.getById(6L)).thenReturn(job);
        var file=new UnCommonFile(); file.setId(8L); file.setSystemId(1L); file.setTenantId(2L);
        file.setModuleId(7L); file.setStatus("EXPORT_RESULT"); file.setOriginalName("result.csv");
        file.setSizeBytes(2L); file.setStorageKey("aa/00000000-0000-0000-0000-000000000000");
        when(files.getById(8L)).thenReturn(file);
        var field=new UnModuleField(); field.setId(11L); field.setFieldKey("name");
        when(config.currentFields(7L)).thenReturn(List.of(field));
        var definition=mock(Definition.class);
        when(definition.settings()).thenReturn(new Settings("导出","EXPORT","TOOLBAR",List.of(),List.of(),null,List.of()));
        var allowed=new PermissionPolicy(true,true,Set.of(),true,Set.of(),true,Set.of(),true,Set.of());
        when(permissions.require(any(),eq(1L),eq(2L),eq(7L),eq("EXPORT")))
                .thenReturn(new ModulePermissionService.Access(context,3L,allowed,definition));
        when(storage.open(any())).thenReturn(new ByteArrayInputStream(new byte[]{1,2}));

        assertEquals("result.csv",service.content("token",1L,2L,7L,6L).name());

        var revoked=new PermissionPolicy(true,true,Set.of(),true,Set.of(),true,Set.of(),false,Set.of());
        when(permissions.require(any(),eq(1L),eq(2L),eq(7L),eq("EXPORT")))
                .thenReturn(new ModulePermissionService.Access(context,3L,revoked,definition));
        assertThrows(BaseException.class,()->service.content("token",1L,2L,7L,6L));
    }
}
