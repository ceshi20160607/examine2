package com.unique.unexamine.sys.module.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.config.ApiExceptionHandler;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.module.manage.bo.DictionarySearchBO;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto.Item;
import com.unique.unexamine.sys.module.manage.service.DictionaryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证实际 HTTP 入口的显式 Result、统一 Page 和业务错误响应。 */
class DictionaryControllerContractTest {
    private final DictionaryService service = mock(DictionaryService.class);

    @Test
    void listReturnsDeclaredEnvelopeAndPage() throws Exception {
        var item = new Item(7L,"BOOLEAN","1","是",null,0,0,null,null,false);
        var page = new Page<Item>(2,5,1).setRecords(java.util.List.of(item));
        when(service.list(eq("Bearer token"),eq(1L),eq(2L),any(DictionarySearchBO.class)))
                .thenReturn(page);

        MockMvcBuilders.standaloneSetup(new DictionaryController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(get("/api/systems/1/dictionaries")
                        .header("Authorization","Bearer token").header("X-Tenant-Id","2")
                        .param("page","2").param("limit","5").param("dictCode","BOOLEAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.records[0].value").value("1"));
    }

    @Test
    void missingItemUsesSameEnvelopeWithHttpStatus() throws Exception {
        when(service.get("Bearer token",1L,2L,7L))
                .thenThrow(new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"字典基础项不存在"));

        MockMvcBuilders.standaloneSetup(new DictionaryController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(get("/api/systems/1/dictionaries/7")
                        .header("Authorization","Bearer token").header("X-Tenant-Id","2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("字典基础项不存在"));
    }
}
