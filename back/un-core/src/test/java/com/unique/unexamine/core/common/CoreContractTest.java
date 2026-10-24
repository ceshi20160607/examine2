package com.unique.unexamine.core.common;

import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 核对公共查询、响应码与业务异常的工程契约。 */
class CoreContractTest {
    /** 基础查询负责统一分页，业务对象可继承并加入自己的条件。 */
    @Test
    void searchBoSupportsBusinessExtension() {
        class LogSearchBO extends SearchBO {
            private Long tenantId;
        }
        var search = new LogSearchBO();
        search.tenantId = 12L;
        search.setKeyword("合同");
        search.setPage(2);
        search.setLimit(20);
        var page = search.<String>parse();
        assertEquals(2, page.getCurrent());
        assertEquals(20, page.getSize());
        assertEquals(12L, search.tenantId);
        assertEquals("合同", search.getKeyword());
        search.setLimit(0);
        assertEquals(SystemCodeEnum.SYSTEM_NO_VALID.getCode(),
                assertThrows(BaseException.class, search::parse).getCode());
    }

    /** 业务错误保留稳定代码，成功与失败共用同一响应结构。 */
    @Test
    void resultAndBusinessExceptionShareCodeContract() {
        assertTrue(Result.ok("业务数据").hasSuccess());
        assertEquals("业务数据", Result.ok("业务数据").data());
        var failure = new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "记录不存在");
        assertFalse(Result.error(failure).hasSuccess());
        assertEquals(404, Result.error(failure).code());
        assertEquals("记录不存在", Result.error(failure).msg());
    }
}
