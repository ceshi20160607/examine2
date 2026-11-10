package com.unique.unexamine.sys.manage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.event.MessageDeliveryTransactions;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysMessage;
import com.unique.unexamine.sys.base.entity.UnSysMessageDeliveryLog;
import com.unique.unexamine.sys.base.entity.UnSysMessageTemplate;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysMessageDeliveryLogService;
import com.unique.unexamine.sys.base.service.IUnSysMessageService;
import com.unique.unexamine.sys.base.service.IUnSysMessageTemplateService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.SendRequest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证模板站内投递的业务结果和重复请求去重，不重复测试基础 CRUD。 */
class SystemMessageTemplateServiceTest {
    private final SystemManageService systems = mock(SystemManageService.class);
    private final IUnSysMessageTemplateService templates = mock(IUnSysMessageTemplateService.class);
    private final IUnSysMessageDeliveryLogService deliveries = mock(IUnSysMessageDeliveryLogService.class);
    private final IUnSysMessageService messages = mock(IUnSysMessageService.class);
    private final IUnSysMemberService members = mock(IUnSysMemberService.class);
    private final IUnSysTenantMemberService tenantMembers = mock(IUnSysTenantMemberService.class);
    private final MessageDeliveryTransactions transactions = mock(MessageDeliveryTransactions.class);
    private final AtomicReference<UnSysMessageDeliveryLog> stored = new AtomicReference<>();
    private final AtomicBoolean failNextMessage = new AtomicBoolean();
    private final SystemMessageTemplateService service = new SystemMessageTemplateService(
            systems, templates, deliveries, messages, members, tenantMembers, new ObjectMapper(), transactions);

    @BeforeEach
    void deliveryStore() {
        when(transactions.execute(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        when(deliveries.getOne(any(), eq(false))).thenAnswer(call -> stored.get());
        when(deliveries.getById(any())).thenAnswer(call -> stored.get());
        when(deliveries.addOrUpdate(any())).thenAnswer(call -> {
            UnSysMessageDeliveryLog log = call.getArgument(0);
            if (log.getId() == null) log.setId(31L);
            stored.set(log);
            return log;
        });
        when(messages.addOrUpdate(any())).thenAnswer(call -> {
            if (failNextMessage.getAndSet(false)) return null;
            UnSysMessage message = call.getArgument(0);
            message.setId(41L);
            return message;
        });
    }

    @Test
    void sendsRenderedMessageOnceForSameBusinessEvent() {
        when(systems.requireAdminContext("admin", 1L, 2L))
                .thenReturn(new SystemContext(1L, 2L, 3L, 4L, 5L, true, true));
        UnSysMessageTemplate template = new UnSysMessageTemplate();
        template.setId(11L); template.setSystemId(1L); template.setTenantId(2L);
        template.setTemplateCode("TASK_NOTICE"); template.setStatus("PUBLISHED");
        template.setVariableKeysJson("[\"title\"]"); template.setTargetType("WORK_TASK");
        String placeholder = "$" + "{title}";
        template.setTitleTemplate("任务 " + placeholder);
        template.setContentTemplate("请处理 " + placeholder);
        when(templates.getOne(any(), eq(false))).thenReturn(template);
        UnSysMember member = new UnSysMember();
        member.setId(7L); member.setSystemId(1L); member.setStatus("ACTIVE");
        when(members.getById(7L)).thenReturn(member);
        when(tenantMembers.exists(any())).thenReturn(true);
        var request = new SendRequest("TASK_NOTICE", 7L,
                Map.of("title", "A " + placeholder), 23L, "task:23");
        service.send("admin", 1L, 2L, request);
        var repeated = service.send("admin", 1L, 2L, request);
        ArgumentCaptor<UnSysMessage> captured = ArgumentCaptor.forClass(UnSysMessage.class);
        verify(messages).addOrUpdate(captured.capture());
        assertEquals("任务 A " + placeholder, captured.getValue().getTitle());
        assertEquals("请处理 A " + placeholder, captured.getValue().getContent());
        assertEquals(31L, repeated.id());
        verify(deliveries, times(2)).addOrUpdate(any(UnSysMessageDeliveryLog.class));
    }

    @Test
    void failedWriteKeepsOriginalContentForManualRetry() {
        when(systems.requireAdminContext("admin", 1L, 2L))
                .thenReturn(new SystemContext(1L, 2L, 3L, 4L, 5L, true, true));
        UnSysMessageTemplate template = new UnSysMessageTemplate();
        template.setId(11L); template.setStatus("PUBLISHED"); template.setTargetType("WORK_TASK");
        template.setVariableKeysJson("[\"title\"]");
        template.setTitleTemplate("任务 " + "$" + "{title}"); template.setContentTemplate("请处理");
        when(templates.getOne(any(), eq(false))).thenReturn(template);
        UnSysMember member = new UnSysMember();
        member.setId(7L); member.setSystemId(1L); member.setStatus("ACTIVE");
        when(members.getById(7L)).thenReturn(member);
        when(tenantMembers.exists(any())).thenReturn(true);
        failNextMessage.set(true);
        var failed = service.send("admin", 1L, 2L,
                new SendRequest("TASK_NOTICE", 7L, Map.of("title", "交付"), 23L, "task:23"));
        assertEquals("FAILED", failed.status());
        assertEquals(1, failed.attemptCount());
        assertEquals("任务 交付", stored.get().getRenderedTitle());
        var retried = service.retry("admin", 1L, 2L, 11L, 31L);
        assertEquals("DELIVERED", retried.status());
        assertEquals(2, retried.attemptCount());
        assertEquals(41L, retried.messageId());
    }

    @Test
    void rejectsRecipientOutsideCurrentSystem() {
        when(systems.requireAdminContext("admin", 1L, 2L))
                .thenReturn(new SystemContext(1L, 2L, 3L, 4L, 5L, true, true));
        UnSysMessageTemplate template = new UnSysMessageTemplate();
        template.setId(11L); template.setStatus("PUBLISHED");
        template.setVariableKeysJson("[]"); template.setTitleTemplate("通知");
        template.setContentTemplate("正文");
        when(templates.getOne(any(), eq(false))).thenReturn(template);
        UnSysMember member = new UnSysMember();
        member.setId(7L); member.setSystemId(99L); member.setStatus("ACTIVE");
        when(members.getById(7L)).thenReturn(member);
        assertThrows(BaseException.class, () -> service.send("admin", 1L, 2L,
                new SendRequest("TASK_NOTICE", 7L, Map.of(), null, null)));
        verify(messages, never()).addOrUpdate(any(UnSysMessage.class));
    }

    @Test
    void assignedTaskUsesMemberContextWithoutAdminPrivilege() {
        when(systems.context("worker", 1L, 2L))
                .thenReturn(new SystemContext(1L, 2L, 3L, 4L, 5L, false, false));
        when(templates.exists(any())).thenReturn(true);
        UnSysMessageTemplate template = new UnSysMessageTemplate();
        template.setId(11L); template.setStatus("PUBLISHED"); template.setTargetType("WORK_TASK");
        template.setVariableKeysJson("[\"title\"]");
        template.setTitleTemplate("任务 " + "$" + "{title}");
        template.setContentTemplate("请处理");
        when(templates.getOne(any(), eq(false))).thenReturn(template);
        UnSysMember member = new UnSysMember();
        member.setId(7L); member.setSystemId(1L); member.setStatus("ACTIVE");
        when(members.getOne(any(), eq(false))).thenReturn(member);
        when(members.getById(7L)).thenReturn(member);
        when(tenantMembers.exists(any())).thenReturn(true);
        service.taskAssigned("worker", 1L, 2L, 8L, 23L, 42L, "交付");
        verify(messages).addOrUpdate(any(UnSysMessage.class));
        verify(systems, never()).requireAdminContext("worker", 1L, 2L);
    }

    @Test
    void flowCandidateReceivesScopedTodoMessage() {
        when(templates.exists(any())).thenReturn(true);
        UnSysMessageTemplate template = new UnSysMessageTemplate();
        template.setId(12L); template.setStatus("PUBLISHED"); template.setTargetType("FLOW_TASK");
        template.setVariableKeysJson("[\"nodeCode\"]");
        template.setTitleTemplate("流程待办 " + "$" + "{nodeCode}");
        template.setContentTemplate("请处理");
        when(templates.getOne(any(), eq(false))).thenReturn(template);
        UnSysMember member = new UnSysMember();
        member.setId(7L); member.setSystemId(1L); member.setStatus("ACTIVE");
        when(members.getOne(any(), eq(false))).thenReturn(member);
        when(members.getById(7L)).thenReturn(member);
        when(tenantMembers.exists(any())).thenReturn(true);
        service.flowTaskAvailable(1L, 2L, 8L, 23L, "review");
        ArgumentCaptor<UnSysMessage> message = ArgumentCaptor.forClass(UnSysMessage.class);
        verify(messages).addOrUpdate(message.capture());
        assertEquals("FLOW_TASK", message.getValue().getTargetType());
        assertEquals(23L, message.getValue().getTargetId());
        assertEquals("流程待办 review", message.getValue().getTitle());
    }

    @Test
    void exportResultLinksToSavedJob() {
        when(templates.exists(any())).thenReturn(true);
        UnSysMessageTemplate template = new UnSysMessageTemplate();
        template.setId(13L); template.setStatus("PUBLISHED"); template.setTargetType("EXPORT_JOB");
        template.setVariableKeysJson("[\"moduleId\",\"status\",\"totalCount\",\"successCount\",\"failureCount\"]");
        template.setTitleTemplate("导出 " + "$" + "{status}");
        template.setContentTemplate("模块 " + "$" + "{moduleId}");
        when(templates.getOne(any(), eq(false))).thenReturn(template);
        UnSysMember member = new UnSysMember();
        member.setId(7L); member.setSystemId(1L); member.setStatus("ACTIVE");
        when(members.getOne(any(), eq(false))).thenReturn(member);
        when(members.getById(7L)).thenReturn(member);
        when(tenantMembers.exists(any())).thenReturn(true);
        service.exchangeCompleted(1L, 2L, 8L, 9L, 23L, "EXPORT", "SUCCESS", 2, 2, 0);
        ArgumentCaptor<UnSysMessage> message = ArgumentCaptor.forClass(UnSysMessage.class);
        verify(messages).addOrUpdate(message.capture());
        assertEquals("EXPORT_JOB", message.getValue().getTargetType());
        assertEquals(23L, message.getValue().getTargetId());
        assertEquals("导出 SUCCESS", message.getValue().getTitle());
    }
}
