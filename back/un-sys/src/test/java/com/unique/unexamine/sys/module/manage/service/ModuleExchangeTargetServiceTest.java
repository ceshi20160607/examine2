package com.unique.unexamine.sys.module.manage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleExchangeJob;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeJobService;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.JobView;
import org.junit.jupiter.api.Test;

/** 消息跳转只解析本人当前租户可读取的作业，不通过编号泄漏其他租户目标。 */
class ModuleExchangeTargetServiceTest {
    @Test
    void resolvesImportThroughExistingAuthorizationAndRejectsOtherTenant() {
        var jobs=mock(IUnModuleExchangeJobService.class);
        var imports=mock(ModuleImportService.class);
        var exports=mock(ModuleExportJobService.class);
        var systems=mock(SystemManageService.class);
        var service=new ModuleExchangeTargetService(jobs,imports,exports,systems);
        var row=new UnModuleExchangeJob();
        row.setId(1L);row.setSystemId(12L);row.setTenantId(12L);
        row.setModuleId(7L);row.setJobType("IMPORT");
        when(jobs.getById(1L)).thenReturn(row);
        when(systems.context("owner",12L,12L))
                .thenReturn(new SystemContext(12L,12L,3L,4L,5L,false,false));
        when(imports.job("owner",12L,12L,7L,1L))
                .thenReturn(new JobView(1L,7L,"CREATE","SUCCESS",2,2,0,null,null));
        var target=service.resolve("owner",12L,12L,1L);
        assertEquals(7L,target.moduleId());
        assertEquals("IMPORT",target.jobType());
        verify(imports).job("owner",12L,12L,7L,1L);

        when(systems.context("other",12L,13L))
                .thenReturn(new SystemContext(12L,13L,8L,9L,10L,false,false));
        assertThrows(BaseException.class,()->service.resolve("other",12L,13L,1L));
    }
}
