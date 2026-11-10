package com.unique.unexamine.work.manage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.common.base.entity.UnCommonAi;
import com.unique.unexamine.core.common.base.entity.UnCommonAiConversation;
import com.unique.unexamine.core.common.base.service.IUnCommonAiService;
import com.unique.unexamine.core.common.base.service.IUnCommonAiConversationService;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import com.unique.unexamine.plat.manage.service.LocalAiModelService;
import com.unique.unexamine.plat.manage.service.PlatformLogManageService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemView;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.plat.manage.bo.PlatformLogSearchBO;
import com.unique.unexamine.plat.manage.dto.PlatformLogDto.LogView;
import com.unique.unexamine.work.manage.bo.WorkTaskSearchBO;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.ConfirmRequest;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.DialogRequest;
import com.unique.unexamine.work.manage.dto.WorkTaskDto.CreateRequest;
import com.unique.unexamine.work.manage.dto.WorkTaskDto.TaskView;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 平台 Agent 写入只走平台普通任务且必须经过一次性确认。 */
class PlatformAiRuntimeServiceTest {
    @Test
    void previewDoesNotWriteAndConfirmCreatesOnlyPlatformTaskOnce() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""),
                UnCommonAiConversation.class);
        AuthManageService auth = mock(AuthManageService.class);
        IUnCommonAiService configs = mock(IUnCommonAiService.class);
        IUnCommonAiConversationService conversations = mock(IUnCommonAiConversationService.class);
        when(conversations.save(any(UnCommonAiConversation.class))).thenReturn(true);
        LocalAiModelService modelCalls = mock(LocalAiModelService.class);
        WorkTaskService tasks = mock(WorkTaskService.class);
        SystemManageService systems = mock(SystemManageService.class);
        PlatformLogManageService platformLogs = mock(PlatformLogManageService.class);
        IUnPlatLogOptService logs = mock(IUnPlatLogOptService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        ObjectMapper json = new ObjectMapper();
        UnPlatAccount account = new UnPlatAccount(); account.setId(11L);
        when(auth.requireAccount("token")).thenReturn(account);
        when(auth.requirePlatformAdmin("token")).thenReturn(account);
        UnCommonAi model = new UnCommonAi();
        model.setId(2L); model.setScopeType("PLATFORM"); model.setStatus("PUBLISHED");
        model.setProviderCode("LOCAL"); model.setPublishedVersion(3);
        model.setPublishedConfigJson("{\"capabilities\":[\"QUERY\",\"WRITE\"],\"conversationRetentionDays\":90}");
        when(configs.getById(2L)).thenReturn(model);
        when(redis.opsForValue()).thenReturn(values);
        when(modelCalls.complete(eq(model), any(String.class), any(String.class))).thenReturn(
                json.readTree("{\"intent\":\"QUERY_TASKS\",\"keyword\":null}"),
                json.readTree("{\"intent\":\"QUERY_LOGS\",\"keyword\":null}"),
                json.readTree("{\"intent\":\"QUERY_SYSTEMS\",\"keyword\":null}"),
                json.readTree("{\"intent\":\"CREATE_TASK\",\"title\":\"平台任务\","
                        + "\"description\":null,\"dueTime\":null,\"confidence\":0.95}"));
        when(tasks.queryPageList(eq("token"), eq(null), eq(null), any(WorkTaskSearchBO.class)))
                .thenReturn(new Page<TaskView>(1, 20, 0).setRecords(List.of()));
        when(platformLogs.queryPageList(eq("token"), any(PlatformLogSearchBO.class)))
                .thenReturn(new Page<LogView>(1, 20, 0).setRecords(List.of()));
        when(systems.list("token")).thenReturn(List.of(new SystemView(9L, "test", "测试系统", 10L, false, false)));
        PlatformAiRuntimeService service = new PlatformAiRuntimeService(auth, configs, conversations, modelCalls,
                tasks, systems, platformLogs, logs, redis, manager, json);
        when(modelCalls.usage(model)).thenReturn(new LocalAiModelService.Usage("2026-11", 100, 2, 3, 0));
        assertEquals(3L, service.quota("token", 2L).usedCalls());

        assertEquals("QUERY_TASKS", service.queryTasks("token", new DialogRequest(2L, "我的任务"))
                .intent());
        assertEquals("QUERY_LOGS", service.queryLogs("token", new DialogRequest(2L, "平台日志"))
                .intent());
        assertEquals(9L, service.querySystems("token", new DialogRequest(2L, "我的系统"))
                .systems().getFirst().id());
        var preview = service.previewTask("token", new DialogRequest(2L, "建一个平台任务"));
        assertEquals(true, preview.ready());
        ArgumentCaptor<UnCommonAiConversation> conversation = ArgumentCaptor.forClass(UnCommonAiConversation.class);
        verify(conversations).save(conversation.capture());
        conversation.getValue().setId(88L);
        when(conversations.getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), eq(false)))
                .thenReturn(conversation.getValue());
        when(conversations.update(any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class)))
                .thenReturn(true);
        verify(tasks, never()).create(eq("token"), eq(null), eq(null), any(CreateRequest.class));
        ArgumentCaptor<String> draft = ArgumentCaptor.forClass(String.class);
        verify(values).set(any(String.class), draft.capture(), any(Duration.class));
        when(values.getAndDelete(any(String.class))).thenReturn(draft.getValue(), null);
        when(tasks.create(eq("token"), eq(null), eq(null), any(CreateRequest.class)))
                .thenReturn(new TaskView(7L, "PLATFORM", null, null, null, null,
                        "平台任务", null, 11L, 11L, "NOT_STARTED", false, BigDecimal.ZERO,
                        null, null, null));
        assertEquals(7L, service.confirmTask("token", new ConfirmRequest(preview.confirmationToken())).taskId());
        assertEquals("建一个平台任务", service.conversation("token", preview.conversationId()).requestText());
        assertThrows(BaseException.class, () -> service.confirmTask("token",
                new ConfirmRequest(preview.confirmationToken())));
    }
}
