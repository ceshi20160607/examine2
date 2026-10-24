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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 验证导出 HTTP 入口只接收导出查询结果，并正确处理展示值与公式文本。 */
class ModuleExportServiceTest {
    private final ModuleRecordListService lists=mock(ModuleRecordListService.class);
    private final ModuleExportService exports=new ModuleExportService(lists,new ObjectMapper());

    @Test void downloadsCurrentFilteredColumnsAsCsv() throws Exception {
        var result=new ModuleRecordListDto.ListView(
                List.of(new ModuleRecordListDto.Column(11L,"name","客户",1,null,null),
                        new ModuleRecordListDto.Column(12L,"level","等级",5,null,null),
                        new ModuleRecordListDto.Column(13L,"balance","余额",3,null,null)),
                new Page<ModuleRecordListDto.Row>(1,500,1).setRecords(List.of(
                        new ModuleRecordListDto.Row(21L,"=SUM(1)","ACTIVE",1L,2L,false,
                                Map.of("name","=SUM(1)","level",101L,"balance",-12),
                                Map.of("level",Map.of("101","重点客户"))))));
        when(lists.export(eq("token"),eq(1L),eq(2L),eq(3L),any())).thenReturn(result);
        var controller=new ModuleActionController(mock(ModuleMenuService.class),mock(ModuleRecordService.class),exports);

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

    @Test void rejectsOverLimitInsteadOfReturningPartialFile() {
        when(lists.export(any(),any(),any(),any(),any())).thenReturn(
                new ModuleRecordListDto.ListView(List.of(),new Page<ModuleRecordListDto.Row>(1,500,501)));
        var request=new ModuleExportDto.ExportRequest(new ModuleRecordSearchBO(),false);

        var failure=assertThrows(ResponseStatusException.class,
                ()->exports.export("token",1L,2L,3L,request));
        assertEquals(HttpStatus.CONFLICT,failure.getStatusCode());
    }
}
