package com.unique.unexamine.examine.manage.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ModuleRecordProcessContext;
import com.unique.unexamine.examine.base.entity.UnExamineExample;
import com.unique.unexamine.examine.base.service.IUnExamineExampleService;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 任一未结束的审批均阻止再次发起删除审批。 */
class ModuleDeleteApprovalStarterTest {
    @Test
    void runningApprovalBlocksDeleteRequest() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(),""),UnExamineExample.class);
        var handler=mock(ModuleExamineProcessHandler.class,CALLS_REAL_METHODS);
        var examples=mock(IUnExamineExampleService.class);
        when(examples.count(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1L);
        ReflectionTestUtils.setField(handler,"examples",examples);
        var request=new ModuleRecordProcessContext(1L,2L,3L,4L,5L,"DELETE",6L,
                "RECORD_DELETE",Map.of("name","待删除记录"));

        assertThrows(BaseException.class,()->handler.startDeleteApproval(request,Map.of()));
    }
}
