package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.module.manage.controller.ModuleActionController;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 验证导出 HTTP 入口只接收导出查询结果，并正确处理展示值与公式文本。 */
class ModuleExportServiceTest {
    private final ModuleRecordListService lists=mock(ModuleRecordListService.class);
    private final com.unique.unexamine.sys.manage.service.SystemManageService systems=
            mock(com.unique.unexamine.sys.manage.service.SystemManageService.class);
    private final ModuleExportService exports=new ModuleExportService(lists,new ObjectMapper(),
            mock(com.unique.unexamine.core.event.BusinessEventPublisher.class),
            systems,
            mock(com.unique.unexamine.sys.base.service.IUnSysLogOptService.class));

    @org.junit.jupiter.api.BeforeEach void loginContext() {
        when(systems.context(any(),any(),any())).thenReturn(
                new com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext(1L,2L,7L,8L,9L,true,true));
    }

    @Test void downloadsCurrentFilteredColumnsAsCsv() throws Exception {
        var result=new ModuleRecordListDto.ListView(
                List.of(new ModuleRecordListDto.Column(11L,"name","客户",1,null,null),
                        new ModuleRecordListDto.Column(12L,"level","等级",5,null,null),
                        new ModuleRecordListDto.Column(13L,"balance","余额",3,null,null)),
                List.of(),
                new Page<ModuleRecordListDto.Row>(1,500,1).setRecords(List.of(
                        new ModuleRecordListDto.Row(21L,"=SUM(1)","ACTIVE",1L,2L,false,
                                Map.of("name","=SUM(1)","level",101L,"balance",-12),
                                Map.of("level",Map.of("101","重点客户")),Map.of()))));
        when(lists.export(eq("token"),eq(1L),eq(2L),eq(3L),any())).thenReturn(result);
        var controller=new ModuleActionController(mock(ModuleMenuService.class),mock(ModuleRecordService.class),exports,
                mock(ModuleExportJobService.class),
                mock(ModuleImportService.class),mock(ModuleImportRollbackService.class),
                mock(ModulePrintService.class),mock(ModulePrintPdfService.class));

        var response=MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/api/systems/1/modules/3/actions/export")
                        .header("Authorization","token").header("X-Tenant-Id","2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":{\"page\":4,\"size\":20,\"scope\":\"ALL\",\"columnFieldIds\":[11,12,13]},\"displayValues\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse();

        String csv=new String(response.getContentAsByteArray(),StandardCharsets.UTF_8);
        assertEquals("\uFEFF\"客户\",\"等级\",\"余额\"\r\n\"'=SUM(1)\",\"重点客户\",\"-12\"\r\n",csv);
        assertTrue(response.getHeader("Content-Disposition").contains("module-3-export.csv"));
        verify(lists).export(eq("token"),eq(1L),eq(2L),eq(3L),argThat(query->
                query.getPage()==1 && query.getLimit()==500
                        && query.getColumnFieldIds().equals(List.of(11L,12L,13L))));
    }

    @Test void exportsEveryPageBeyondFiveHundredRows() {
        var column=List.of(new ModuleRecordListDto.Column(11L,"name","名称",1,null,null));
        var first=java.util.stream.IntStream.rangeClosed(1,500).mapToObj(id ->
                new ModuleRecordListDto.Row((long)id,"row-"+id,"ACTIVE",1L,2L,false,
                        Map.of("name","row-"+id),Map.of(),Map.of())).toList();
        var second=List.of(new ModuleRecordListDto.Row(501L,"row-501","ACTIVE",1L,2L,false,
                Map.of("name","row-501"),Map.of(),Map.of()));
        when(lists.export(any(),any(),any(),any(),any())).thenAnswer(call -> {
            ModuleRecordSearchBO query=call.getArgument(4);
            return new ModuleRecordListDto.ListView(column,List.of(),
                    new Page<ModuleRecordListDto.Row>(query.getPage(),500,501)
                            .setRecords(query.getPage()==1?first:second));
        });
        var file=exports.export("token",1L,2L,3L,
                new ModuleExportDto.ExportRequest(new ModuleRecordSearchBO(),false));
        String csv=new String(file.content(),StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"row-501\"\r\n"));
        assertEquals(502,csv.split("\r\n").length);
        verify(lists,times(2)).export(any(),any(),any(),any(),any());
    }

    @Test void rejectsOverLimitInsteadOfReturningPartialFile() {
        when(lists.export(any(),any(),any(),any(),any())).thenReturn(
                new ModuleRecordListDto.ListView(List.of(),List.of(),new Page<ModuleRecordListDto.Row>(1,500,50001)));
        var request=new ModuleExportDto.ExportRequest(new ModuleRecordSearchBO(),false);

        var failure=assertThrows(BaseException.class,
                ()->exports.export("token",1L,2L,3L,request));
        assertEquals(SystemCodeEnum.SYSTEM_CONFLICT.getCode(),failure.getCode());
    }
}
