package com.unique.unexamine.sys.manage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.plat.manage.service.PlatformAiConfigService;
import com.unique.unexamine.plat.manage.service.LocalAiModelService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.QueryRequest;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.WriteConfirmRequest;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.WritePreviewRequest;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.Column;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.ListView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.Row;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RecordView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.SaveRecordRequest;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import com.unique.unexamine.sys.module.manage.service.ModulePermissionService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordListService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 用本地模型协议响应核对 Agent 字段范围与查询委托。 */
class SystemAiRuntimeServiceTest {
    @Test
    void queryRestrictsFieldsAndWriteRequiresOneTimeConfirmation() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""),
                UnCommonAiConversation.class);
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            String queryReply = "{\"choices\":[{\"message\":{\"content\":\"{\\\"intent\\\":\\\"QUERY\\\",\\\"keyword\\\":null}\"}}]}";
            String writeReply = "{\"choices\":[{\"message\":{\"content\":\"{\\\"intent\\\":\\\"CREATE\\\",\\\"values\\\":{\\\"name\\\":\\\"新记录\\\"},\\\"confidence\\\":{\\\"name\\\":0.95}}\"}}]}";
            Thread modelServer = Thread.ofVirtual().start(() -> {
                try {
                    for (String reply : List.of(queryReply, writeReply)) {
                        try (var socket = server.accept()) {
                            var input = socket.getInputStream();
                            ByteArrayOutputStream headers = new ByteArrayOutputStream();
                            while (!headers.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
                                int next = input.read();
                                if (next < 0 || headers.size() > 8192) throw new IllegalStateException("请求头无效");
                                headers.write(next);
                            }
                            String header = headers.toString(StandardCharsets.US_ASCII);
                            int length = Integer.parseInt(header.lines().filter(line -> line.toLowerCase()
                                    .startsWith("content-length:")).findFirst().orElseThrow().split(":", 2)[1].trim());
                            input.readNBytes(length);
                            byte[] payload = reply.getBytes(StandardCharsets.UTF_8);
                            var output = socket.getOutputStream();
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                                    + payload.length + "\r\nConnection: close\r\n\r\n")
                                    .getBytes(StandardCharsets.US_ASCII));
                            output.write(payload); output.flush();
                        }
                    }
                } catch (Exception ex) { throw new IllegalStateException(ex); }
            });
            SystemManageService systems = mock(SystemManageService.class);
            IUnCommonAiService configs = mock(IUnCommonAiService.class);
            IUnCommonAiConversationService conversations = mock(IUnCommonAiConversationService.class);
            when(conversations.save(any(UnCommonAiConversation.class))).thenReturn(true);
            PlatformAiConfigService platform = mock(PlatformAiConfigService.class);
            ModulePermissionService permissions = mock(ModulePermissionService.class);
            ModuleConfigService modules = mock(ModuleConfigService.class);
            ModuleRecordListService lists = mock(ModuleRecordListService.class);
            ModuleRecordService records = mock(ModuleRecordService.class);
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
            IUnSysLogOptService logs = mock(IUnSysLogOptService.class);
            SystemContext context = new SystemContext(10L, 20L, 30L, 40L, 50L, false, false);
            when(systems.context("token", 10L, 20L)).thenReturn(context);
            UnCommonAi systemConfig = new UnCommonAi();
            systemConfig.setId(1L); systemConfig.setScopeType("SYSTEM");
            systemConfig.setSystemId(10L); systemConfig.setTenantId(20L);
            systemConfig.setStatus("PUBLISHED"); systemConfig.setPublishedVersion(1);
            systemConfig.setPublishedConfigJson("{\"platformConfigId\":2,\"platformPublishedVersion\":3,"
                    + "\"policy\":{\"moduleIds\":[4],\"fieldIds\":[6],\"actions\":[\"QUERY\"],"
                    + "\"promptVersion\":\"v1\",\"conversationRetentionDays\":90}}");
            when(configs.getById(1L)).thenReturn(systemConfig);
            UnCommonAi model = new UnCommonAi();
            model.setId(2L); model.setProviderCode("LOCAL"); model.setPublishedVersion(3);
            model.setPublishedConfigJson("{\"model\":\"test\",\"apiUrl\":\"http://127.0.0.1:"
                    + server.getLocalPort() + "/chat\",\"capabilities\":[\"QUERY\"],"
                    + "\"monthlyQuota\":10,\"maxConcurrency\":2}");
            when(platform.requireAuthorizedModel(2L, 10L)).thenReturn(model);
            PermissionPolicy policy = new PermissionPolicy(true, true, Set.of(), true, Set.of(),
                    true, Set.of(), true, Set.of());
            when(permissions.require("token", 10L, 20L, 4L, "LIST"))
                    .thenReturn(new ModulePermissionService.Access(context, 30L, policy, null));
            UnModuleField name = new UnModuleField();
            name.setId(5L); name.setFieldKey("name");
            UnModuleField secret = new UnModuleField();
            secret.setId(6L); secret.setFieldKey("secret");
            when(modules.currentFields(4L)).thenReturn(List.of(name, secret));
            ListView list = new ListView(List.of(new Column(6L, "secret", "秘密", 1, null, null)),List.of(),
                    new Page<Row>(1, 20, 1).setRecords(List.of(new Row(7L, "不可返回的标题", "ACTIVE", 30L,
                            20L, false, Map.of("secret", "可见"), Map.of(),Map.of()))));
            when(lists.query(eq("token"), eq(10L), eq(20L), eq(4L), any(ModuleRecordSearchBO.class)))
                    .thenReturn(list);
            doReturn(0L).when(redis).execute(any(RedisScript.class), anyList(), any(String.class), any(String.class));
            when(redis.opsForValue()).thenReturn(values);
            SystemAiRuntimeService service = new SystemAiRuntimeService(systems, configs, conversations, platform,
                    new LocalAiModelService(redis, new ObjectMapper()),
                    permissions, modules, lists, records, logs, redis, manager, new ObjectMapper());
            var result = service.query("token", 10L, 20L, new QueryRequest(1L, 4L, "查看数据"));

            assertEquals("QUERY", result.intent());
            assertNull(result.result().page().getRecords().getFirst().name());
            assertNull(result.result().page().getRecords().getFirst().status());
            assertEquals(Map.of("secret", "可见"), result.result().page().getRecords().getFirst().values());
            when(configs.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                    .thenReturn(List.of(systemConfig));
            assertEquals(List.of("QUERY"), service.available("token", 10L, 20L).getFirst().actions());

            systemConfig.setPublishedConfigJson("{\"platformConfigId\":2,\"platformPublishedVersion\":3,"
                    + "\"policy\":{\"moduleIds\":[4],\"fieldIds\":[5,6],\"actions\":[\"QUERY\",\"WRITE\"],"
                    + "\"promptVersion\":\"v1\",\"conversationRetentionDays\":90}}");
            model.setPublishedConfigJson(model.getPublishedConfigJson().replace(
                    "\"capabilities\":[\"QUERY\"]", "\"capabilities\":[\"QUERY\",\"WRITE\"]"));
            when(permissions.require("token", 10L, 20L, 4L, "CREATE"))
                    .thenReturn(new ModulePermissionService.Access(context, 30L, policy, null));
            var preview = service.preview("token", 10L, 20L,
                    new WritePreviewRequest(1L, 4L, "新增一个名为新记录的数据"));
            assertEquals(true, preview.ready());
            ArgumentCaptor<UnCommonAiConversation> conversation = ArgumentCaptor.forClass(UnCommonAiConversation.class);
            verify(conversations).save(conversation.capture());
            conversation.getValue().setId(88L);
            when(conversations.getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), eq(false)))
                    .thenReturn(conversation.getValue());
            when(conversations.update(any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class)))
                    .thenReturn(true);
            verify(records, never()).save(eq("token"), eq(10L), eq(20L), eq(4L), any(SaveRecordRequest.class));
            ArgumentCaptor<String> draft = ArgumentCaptor.forClass(String.class);
            verify(values).set(any(String.class), draft.capture(), any(Duration.class));
            when(values.getAndDelete(any(String.class))).thenReturn(draft.getValue(), null);
            when(records.save(eq("token"), eq(10L), eq(20L), eq(4L), any(SaveRecordRequest.class)))
                    .thenReturn(new RecordView(99L, "新记录", "NORMAL", 0, 30L, 30L, null, 20L, false));
            assertEquals(99L, service.confirm("token", 10L, 20L,
                    new WriteConfirmRequest(preview.confirmationToken())).recordId());
            assertEquals("新增一个名为新记录的数据", service.conversation("token", 10L, 20L,
                    preview.conversationId()).requestText());
            assertThrows(BaseException.class, () -> service.confirm("token", 10L, 20L,
                    new WriteConfirmRequest(preview.confirmationToken())));
            modelServer.join(5000);
        }
    }
}
