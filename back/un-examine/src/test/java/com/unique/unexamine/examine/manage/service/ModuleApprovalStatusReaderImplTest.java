package com.unique.unexamine.examine.manage.service;

import com.unique.unexamine.examine.base.entity.UnExamineExample;
import com.unique.unexamine.examine.base.entity.UnModuleExamine;
import com.unique.unexamine.examine.base.service.IUnExamineExampleService;
import com.unique.unexamine.examine.base.service.IUnModuleExamineService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 多个动作共用状态组时，当前字段取该组最近发起的审批实例状态。 */
class ModuleApprovalStatusReaderImplTest {
    @Test
    void latestInstanceWinsWithinSharedGroup() {
        var mapper=new MapperBuilderAssistant(new Configuration(),"");
        TableInfoHelper.initTableInfo(mapper,UnModuleExamine.class);
        TableInfoHelper.initTableInfo(mapper,UnExamineExample.class);
        var bindings=mock(IUnModuleExamineService.class);
        var examples=mock(IUnExamineExampleService.class);
        when(bindings.list(org.mockito.ArgumentMatchers.<Wrapper<UnModuleExamine>>any()))
                .thenReturn(List.of(binding(11L,5L),binding(12L,5L),binding(13L,6L)));
        // 查询已按发起时间、主键倒序排序；较早的完成结果不能盖过新发起的审批中状态。
        when(examples.list(org.mockito.ArgumentMatchers.<Wrapper<UnExamineExample>>any())).thenReturn(List.of(
                example(22L,12L,"RUNNING"),example(21L,11L,"COMPLETED"),
                example(22L,13L,"REJECTED")));
        var reader=new ModuleApprovalStatusReaderImpl(bindings,examples);

        var result=reader.read(1L,2L,3L,List.of(22L,21L),List.of(5L,6L));

        assertEquals("RUNNING",result.get(22L).get(5L));
        assertEquals("REJECTED",result.get(22L).get(6L));
        assertEquals("COMPLETED",result.get(21L).get(5L));
    }

    private UnModuleExamine binding(Long id,Long groupId) {
        var binding=new UnModuleExamine(); binding.setId(id); binding.setStatusGroupId(groupId);
        return binding;
    }

    private UnExamineExample example(Long recordId,Long bindingId,String status) {
        var instance=new UnExamineExample(); instance.setRecordId(recordId);
        instance.setModuleExamineId(bindingId); instance.setStatus(status);
        return instance;
    }
}
