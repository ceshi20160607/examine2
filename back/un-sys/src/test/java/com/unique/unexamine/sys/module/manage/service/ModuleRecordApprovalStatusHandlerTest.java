package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 启动与终态都同步主表状态，平台审批没有记录主键时不写业务表。 */
class ModuleRecordApprovalStatusHandlerTest {
    @Test
    void synchronizesRunningAndTerminalStatusOnlyForModuleRecords() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(),""),UnModuleRecord.class);
        var records=mock(IUnModuleRecordService.class);
        var handler=new ModuleRecordApprovalStatusHandler(records);
        handler.onEvent(new BusinessEventContext("EXAMINE_STARTED",1L,2L,3L,4L,5L,null,Map.of(),Map.of()));
        handler.afterCompletion(new ExamineCompletionContext(1L,2L,3L,4L,5L,6L,7L,"REJECTED",Map.of(),Map.of()));
        handler.onEvent(new BusinessEventContext("EXAMINE_STARTED",null,null,null,null,5L,null,Map.of(),Map.of()));
        verify(records,times(2)).update(any(Wrapper.class));
    }
}
