package com.unique.unexamine.foundation.manage.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import com.unique.unexamine.foundation.base.service.CoreIdempotencyRecordBaseService;
import com.unique.unexamine.shared.manage.security.RuntimeSecretCipher;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 启动时序、伪造绑定和密钥意图的真实只读边界；不启动数据库或容器。 */
class ApprovalBusinessReadTest {
    private final ObjectMapper json = new ObjectMapper();
    private final BusinessActionMapper mapper = mock(BusinessActionMapper.class);
    private final BusinessActionValueProtection protection = new BusinessActionValueProtection(json,
            new RuntimeSecretCipher("synthetic-read-test-key-for-runtime-only"));
    @SuppressWarnings("unchecked")
    private final BusinessActionApprovalCoordinator coordinator = new BusinessActionApprovalCoordinator(mapper,
            mock(CoreIdempotencyRecordBaseService.class), mock(CoreEventOutboxBaseService.class), mock(PermissionResolver.class),
            mock(ObjectProvider.class), mock(ObjectProvider.class), mock(AuditRecorder.class), json,
            mock(PlatformTransactionManager.class), protection, mock(BusinessActionFormSubmissions.class));

    private FlowInstance flow() {
        FlowInstance flow = new FlowInstance();
        flow.setId(81L); flow.setSystemId(54L); flow.setTenantId(57L); flow.setFlowId(26L);
        flow.setFlowVersionId(34L); flow.setStartedByAccountId(69L); flow.setBusinessType("MODULE:acceptance");
        return flow;
    }

    private ObjectNode payload() {
        AuthenticatedContext actor = new AuthenticatedContext(null, 69L, 1L, 54L, 57L, 101L, 108L,
                "synthetic", "验收成员", "NONE", List.of(), List.of(), Map.of());
        BusinessAction.Input input = new BusinessAction.Input(actor, 57L, 51L, "acceptance", 94L, "CREATE", null, null,
                Map.of("fields", Map.of("credential", "synthetic-proposed-secret", "subject", "普通主题")),
                BusinessAction.Options.page("synthetic-read"), "synthetic-trace");
        ObjectNode payload = json.createObjectNode();
        payload.set("action", json.valueToTree(input));
        payload.set("binding", json.valueToTree(Map.of("flowId", 26L, "flowVersionId", 34L)));
        payload.set("configurationSnapshot", json.valueToTree(Map.of("fields", List.of(Map.of("code", "credential", "fieldType", "SECRET")))));
        payload.put("flowInstanceId", 81L);
        return protection.protect(payload, protection.secretFields(payload.path("configurationSnapshot")));
    }

    private void event(ObjectNode payload) {
        CoreEventOutbox event = new CoreEventOutbox(); event.setSystemId(54L); event.setAggregateType("BUSINESS_ACTION");
        event.setAggregateId("324"); event.setPayloadJson(payload.toString());
        when(mapper.boundFlowEvent(81L)).thenReturn(event);
    }

    @Test void startBeforeOutboxLinkHasNoReadProjection() {
        ObjectNode payload = payload(); payload.remove("flowInstanceId"); event(payload);
        assertNull(coordinator.businessReadForFlow(flow()));
    }

    @Test void boundReadRedactsSecretAndDoesNotBorrowOriginalActorAuthority() {
        ObjectNode payload = payload(); String before = payload.toString(); event(payload);
        var read = coordinator.businessReadForFlow(flow());
        String exposed = json.valueToTree(read.input().payload()).toString();
        assertTrue(exposed.contains("***")); assertFalse(exposed.contains("synthetic-proposed-secret"));
        assertFalse(exposed.contains("runtime-secret:")); assertEquals(before, payload.toString());
        verify(mapper, never()).activeActor(any());
    }

    @Test void settledLinkCannotBeReplayedToAnotherInstance() {
        ObjectNode payload = payload(); payload.put("flowInstanceId", 82L); event(payload);
        assertEquals("ACTION_APPROVAL_FLOW_MISMATCH", assertThrows(DomainException.class,
                () -> coordinator.businessReadForFlow(flow())).code());
    }

    @Test void sourceTenantAndPublishedVersionMustMatch() {
        ObjectNode payload = payload(); payload.withObject("action").put("dataTenantId", 77L); event(payload);
        assertThrows(DomainException.class, () -> coordinator.businessReadForFlow(flow()));
        payload = payload(); payload.withObject("binding").put("flowVersionId", 33L); event(payload);
        assertThrows(DomainException.class, () -> coordinator.businessReadForFlow(flow()));
    }

    @Test void clientSnapshotWithoutServerStartMarkerGrantsNothing() {
        FlowInstance flow = flow(); flow.setBusinessSnapshotJson("{\"actionId\":324,\"fields\":{\"subject\":\"伪造值\"}}");
        assertNull(coordinator.businessReadForFlow(flow));
    }

    @Test void legacyMissingFieldConfigurationDoesNotInvalidateTrustedFlowLink() {
        ObjectNode payload = payload(); payload.remove("configurationSnapshot"); event(payload);
        var read = coordinator.businessReadForFlow(flow());
        assertNotNull(read); assertTrue(read.configurationSnapshot().isMissingNode());
        assertFalse(json.valueToTree(read.input().payload()).toString().contains("synthetic-proposed-secret"));
    }
}
