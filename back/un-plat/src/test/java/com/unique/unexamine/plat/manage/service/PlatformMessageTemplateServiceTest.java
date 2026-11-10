package com.unique.unexamine.plat.manage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.event.MessageDeliveryTransactions;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatMessage;
import com.unique.unexamine.plat.base.entity.UnPlatMessageDeliveryLog;
import com.unique.unexamine.plat.base.entity.UnPlatMessageTemplate;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatMessageDeliveryLogService;
import com.unique.unexamine.plat.base.service.IUnPlatMessageService;
import com.unique.unexamine.plat.base.service.IUnPlatMessageTemplateService;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.SendRequest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** 验证平台消息失败后保留快照，并按首次内容人工重试。 */
class PlatformMessageTemplateServiceTest {
    @Test
    void retriesFailedDeliveryWithoutRerenderingTemplate() {
        AuthManageService auth = mock(AuthManageService.class);
        IUnPlatMessageTemplateService templates = mock(IUnPlatMessageTemplateService.class);
        IUnPlatMessageDeliveryLogService deliveries = mock(IUnPlatMessageDeliveryLogService.class);
        IUnPlatMessageService messages = mock(IUnPlatMessageService.class);
        IUnPlatAccountService accounts = mock(IUnPlatAccountService.class);
        MessageDeliveryTransactions transactions = mock(MessageDeliveryTransactions.class);
        PlatformMessageTemplateService service = new PlatformMessageTemplateService(
                auth, templates, deliveries, messages, accounts, new ObjectMapper(), transactions);
        when(transactions.execute(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        UnPlatMessageTemplate template = new UnPlatMessageTemplate();
        template.setId(11L); template.setStatus("PUBLISHED"); template.setTargetType("PLATFORM_TASK");
        template.setVariableKeysJson("[\"title\"]");
        template.setTitleTemplate("任务 " + "$" + "{title}"); template.setContentTemplate("请处理");
        when(templates.getOne(any(), eq(false))).thenReturn(template);
        when(templates.getById(11L)).thenReturn(template);
        UnPlatAccount account = new UnPlatAccount();
        account.setId(7L); account.setStatus("ACTIVE");
        when(accounts.getById(7L)).thenReturn(account);
        AtomicReference<UnPlatMessageDeliveryLog> stored = new AtomicReference<>();
        when(deliveries.getOne(any(), eq(false))).thenAnswer(call -> stored.get());
        when(deliveries.getById(31L)).thenAnswer(call -> stored.get());
        when(deliveries.addOrUpdate(any())).thenAnswer(call -> {
            UnPlatMessageDeliveryLog log = call.getArgument(0);
            if (log.getId() == null) log.setId(31L);
            stored.set(log);
            return log;
        });
        AtomicInteger writes = new AtomicInteger();
        when(messages.addOrUpdate(any())).thenAnswer(call -> {
            if (writes.getAndIncrement() == 0) return null;
            UnPlatMessage message = call.getArgument(0);
            message.setId(41L);
            return message;
        });

        var failed = service.send("admin", new SendRequest(
                "TASK_NOTICE", 7L, Map.of("title", "交付"), 23L, "task:23"));
        assertEquals("FAILED", failed.status());
        assertEquals("任务 交付", stored.get().getRenderedTitle());
        template.setTitleTemplate("已修改的模板");
        var retried = service.retry("admin", 11L, 31L);
        assertEquals("DELIVERED", retried.status());
        assertEquals(2, retried.attemptCount());
        assertEquals(41L, retried.messageId());
    }
}
