package com.unique.examine.event.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.adapter.memory.InMemoryInboxMessageRepository;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.port.InboxMessageRepository;
import com.unique.examine.event.service.MessageInboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageInboxControllerTest {
    private ObjectMapper json;
    private MockMvc mvc;
    private MessageInboxService service;
    private EventActor sender;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper().registerModule(new JavaTimeModule());
        service = service(new InMemoryInboxMessageRepository());
        mvc = mvc(service);
        sender = new EventActor(10, 20, 100, Set.of(MessageInboxService.CREATE));
    }

    @Test
    void authenticatedRecipientCanListCountReadReadAllAndArchive() throws Exception {
        var first = service.create(sender, 101, "NOTICE", "First", "Body", null);
        var second = service.create(sender, 101, "NOTICE", "Second", "Body", null);
        var recipient = session(10, 20, 101, EventRequestSession.ACCESS);

        var listBody = mvc.perform(get("/api/v1/systems/10/event/messages")
                        .queryParam("status", "ALL")
                        .queryParam("page", "1")
                        .queryParam("size", "1")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, recipient))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var listJson = json.readTree(listBody);
        assertThat(listJson.at("/data/items")).hasSize(1);
        assertThat(listJson.at("/data/page").asInt()).isEqualTo(1);
        assertThat(listJson.at("/data/size").asInt()).isEqualTo(1);
        assertThat(listJson.at("/data/total").asLong()).isEqualTo(2);
        assertThat(listJson.at("/data/items/0/id").isTextual()).isTrue();
        assertThat(listJson.at("/data/items/0/id").asText()).isEqualTo(Long.toString(second.id()));
        assertThat(listJson.at("/data/items/0/systemId").asText()).isEqualTo("10");
        assertThat(listJson.at("/data/items/0/tenantId").asText()).isEqualTo("20");
        assertThat(listJson.at("/data/items/0/senderMemberId").asText()).isEqualTo("100");
        assertThat(listJson.at("/data/items/0/recipientMemberId").asText()).isEqualTo("101");
        assertThat(listJson.path("requestId").asText()).isEmpty();
        assertThat(listJson.path("traceId").asText()).isEmpty();

        var countBody = mvc.perform(get("/api/v1/systems/10/event/messages/unread-count")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, recipient))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(countBody).path("data").path("unreadCount").asLong()).isEqualTo(2);

        mvc.perform(post("/api/v1/systems/10/event/messages/{id}:read", first.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, recipient))
                .andExpect(status().isOk());
        var readAllBody = mvc.perform(post("/api/v1/systems/10/event/messages/read-all")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, recipient))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(readAllBody).path("data").path("changedCount").asInt()).isEqualTo(1);

        mvc.perform(post("/api/v1/systems/10/event/messages/{id}:archive", second.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, recipient))
                .andExpect(status().isOk());
        var activeList = mvc.perform(get("/api/v1/systems/10/event/messages")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, recipient))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(activeList).at("/data/items")).hasSize(1);
        assertThat(json.readTree(activeList).at("/data/total").asLong()).isEqualTo(1);

        var archivedList = mvc.perform(get("/api/v1/systems/10/event/messages")
                        .queryParam("status", "ARCHIVED")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, recipient))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(archivedList).at("/data/items/0/id").asText())
                .isEqualTo(Long.toString(second.id()));
    }

    @Test
    void accessAndRecipientOwnershipFailClosed() throws Exception {
        var message = service.create(sender, 101, "NOTICE", "Private", "Body", null);
        mvc.perform(get("/api/v1/systems/10/event/messages")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session(10, 20, 101)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/systems/10/event/messages/{id}:read", message.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 102, EventRequestSession.ACCESS)))
                .andExpect(status().isNotFound());
    }

    @Test
    void systemMismatchAndTenantIsolationAreRejected() throws Exception {
        var message = service.create(sender, 101, "NOTICE", "Tenant 20", "Body", null);
        mvc.perform(get("/api/v1/systems/11/event/messages")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, EventRequestSession.ACCESS)))
                .andExpect(status().isForbidden());

        var otherTenant = session(10, 21, 101, EventRequestSession.ACCESS);
        var listBody = mvc.perform(get("/api/v1/systems/10/event/messages")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, otherTenant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(listBody).at("/data/items")).isEmpty();
        assertThat(json.readTree(listBody).at("/data/total").asLong()).isZero();
        mvc.perform(post("/api/v1/systems/10/event/messages/{id}:archive", message.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, otherTenant))
                .andExpect(status().isNotFound());
    }

    @Test
    void optimisticVersionConflictReturnsHttp409() throws Exception {
        var repository = new ConflictOnUpdateRepository();
        var conflictService = service(repository);
        var conflictMvc = mvc(conflictService);
        var message = conflictService.create(sender, 101, "NOTICE", "Conflict", "Body", null);

        conflictMvc.perform(post("/api/v1/systems/10/event/messages/{id}:read", message.id())
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, EventRequestSession.ACCESS)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidStatusReturnsAStableClientError() throws Exception {
        mvc.perform(get("/api/v1/systems/10/event/messages")
                        .queryParam("status", "DELETED")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, EventRequestSession.ACCESS)))
                .andExpect(status().isUnprocessableEntity());
    }

    private MessageInboxService service(InboxMessageRepository repository) {
        return new MessageInboxService(repository,
                (systemId, tenantId, memberId) -> systemId == 10
                        && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId),
                Clock.fixed(Instant.parse("2026-07-25T09:00:00Z"), ZoneOffset.UTC));
    }

    private MockMvc mvc(MessageInboxService inboxService) {
        return MockMvcBuilders.standaloneSetup(new MessageInboxController(inboxService))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    private static TestSession session(
            long systemId,
            long tenantId,
            long memberId,
            String... permissions
    ) {
        return new TestSession(systemId, tenantId, memberId, Set.of(permissions));
    }

    private record TestSession(
            long requestedSystemId,
            long requestedTenantId,
            long requestedMemberId,
            Set<String> requestedPermissions
    ) implements RequestSession {
        @Override
        public long sessionId() {
            return 1;
        }

        @Override
        public long accountId() {
            return requestedMemberId;
        }

        @Override
        public ContextType contextType() {
            return ContextType.SYSTEM;
        }

        @Override
        public Long systemId() {
            return requestedSystemId;
        }

        @Override
        public Long tenantId() {
            return requestedTenantId;
        }

        @Override
        public Long memberId() {
            return requestedMemberId;
        }

        @Override
        public long permissionVersion() {
            return 1;
        }

        @Override
        public Set<String> permissions() {
            return requestedPermissions;
        }
    }

    @RestControllerAdvice
    private static final class TestExceptionAdvice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Map<String, Object>> business(BusinessException error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(),
                    "message", error.getMessage()));
        }
    }

    private static final class ConflictOnUpdateRepository implements InboxMessageRepository {
        private final InMemoryInboxMessageRepository delegate = new InMemoryInboxMessageRepository();

        @Override
        public long nextId() {
            return delegate.nextId();
        }

        @Override
        public Optional<InboxMessage> findById(long systemId, long tenantId, long id) {
            return delegate.findById(systemId, tenantId, id);
        }

        @Override
        public List<InboxMessage> findInbox(long systemId, long tenantId, long recipientMemberId) {
            return delegate.findInbox(systemId, tenantId, recipientMemberId);
        }

        @Override
        public long countInbox(
                long systemId,
                long tenantId,
                long recipientMemberId,
                com.unique.examine.event.domain.InboxMessageFilter status
        ) {
            return delegate.countInbox(systemId, tenantId, recipientMemberId, status);
        }

        @Override
        public List<InboxMessage> findInboxPage(
                long systemId,
                long tenantId,
                long recipientMemberId,
                com.unique.examine.event.domain.InboxMessageFilter status,
                long offset,
                int limit
        ) {
            return delegate.findInboxPage(
                    systemId, tenantId, recipientMemberId, status, offset, limit);
        }

        @Override
        public InboxMessage save(InboxMessage message) {
            if (message.version() > 1) {
                throw new EventDomainException("EVENT_MESSAGE_VERSION_CONFLICT", "Message version is stale");
            }
            return delegate.save(message);
        }
    }
}
