package com.unique.unexamine.work.manage.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.work.base.entity.UnWorkDaily;
import com.unique.unexamine.work.base.service.IUnWorkDailyService;
import com.unique.unexamine.work.manage.service.WorkScopeService.Scope;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 手动日志的提交产生新版本，避免覆盖用户此前的草稿记录。 */
class WorkDailyServiceTest {
    @Test
    void submitKeepsOriginalAndCreatesCurrentVersion() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(),""),UnWorkDaily.class);
        WorkScopeService scopes=mock(WorkScopeService.class);
        IUnWorkDailyService dailies=mock(IUnWorkDailyService.class);
        WorkDailyService service=new WorkDailyService(scopes,dailies,
                mock(WorkProjectService.class),mock(WorkTaskService.class),new ObjectMapper());
        Scope scope=new Scope(10L,"PLATFORM",null,null);
        when(scopes.scope("token",null,null)).thenReturn(scope);
        UnWorkDaily draft=new UnWorkDaily();
        draft.setId(7L);draft.setVersionNo(1);draft.setAccountId(10L);
        draft.setScopeType("PLATFORM");draft.setIsCurrent(true);
        draft.setLogDate(LocalDate.of(2026,10,23));
        draft.setCompletedText("完成测试");draft.setStatus("DRAFT");
        when(dailies.getOne(any(),eq(false))).thenReturn(draft);
        when(dailies.addOrUpdate(any(UnWorkDaily.class))).thenAnswer(invocation->{
            UnWorkDaily row=invocation.getArgument(0);row.setId(8L);return row;
        });
        var submitted=service.submit("token",null,null,7L);
        assertEquals(7L,submitted.rootId());
        assertEquals(2,submitted.versionNo());
        assertEquals("SUBMITTED",submitted.status());
        assertEquals("完成测试",submitted.completedText());
        verify(dailies).update(any());
    }

    @Test
    void manuallySelectedProjectAndTaskAreCheckedAndReturned() {
        WorkScopeService scopes=mock(WorkScopeService.class);
        IUnWorkDailyService dailies=mock(IUnWorkDailyService.class);
        WorkProjectService projects=mock(WorkProjectService.class);
        WorkTaskService tasks=mock(WorkTaskService.class);
        WorkDailyService service=new WorkDailyService(scopes,dailies,projects,tasks,new ObjectMapper());
        when(scopes.scope("token",null,null)).thenReturn(new Scope(10L,"PLATFORM",null,null));
        when(dailies.addOrUpdate(any(UnWorkDaily.class))).thenAnswer(invocation->{
            UnWorkDaily row=invocation.getArgument(0);row.setId(7L);return row;
        });
        var result=service.create("token",null,null,new WorkDailyService.DailyRequest(
                LocalDate.of(2026,10,23),"完成测试",null,null,null,null,
                List.of(20L),List.of(40L)));
        assertEquals(List.of(20L),result.relations().projectIds());
        assertEquals(List.of(40L),result.relations().taskIds());
        verify(projects).queryById("token",null,null,20L);
        verify(tasks).queryById("token",null,null,40L);
    }
}
