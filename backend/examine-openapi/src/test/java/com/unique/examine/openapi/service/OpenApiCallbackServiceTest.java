package com.unique.examine.openapi.service;

import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.openapi.api.OpenApiAdminSession;
import com.unique.examine.openapi.api.OpenApiCallbackRequests;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.domain.OpenApiCallbackAttempt;
import com.unique.examine.openapi.domain.OpenApiCallbackDelivery;
import com.unique.examine.openapi.domain.OpenApiCallbackSubscription;
import com.unique.examine.openapi.domain.OpenApiCallbackVersion;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.domain.OpenApiRateBucket;
import com.unique.examine.openapi.repository.OpenApiCallbackRepository;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.security.OpenApiCallbackTargetPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiCallbackServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-06T14:00:00Z");
    private Store callbacks;
    private Audits audits;
    private OpenApiCallbackService service;
    private OpenApiAdminSession session;

    @BeforeEach
    void setUp() {
        callbacks = new Store();
        audits = new Audits();
        session = new OpenApiAdminSession(7, 10, 20, 40,
                Set.of(OpenApiAdminSession.MANAGE_PERMISSION));
        service = new OpenApiCallbackService(callbacks, new Applications(application()),
                reference -> Optional.of("0123456789abcdef0123456789abcdef".getBytes()),
                new OpenApiCallbackTargetPolicy(), audits, new SequentialIds(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsApplicationOwnedVersionedSubscriptionAndRedactsSensitiveConfiguration() {
        var created = service.create(session, 30, new OpenApiCallbackRequests.Create(
                "ERP callback", "https://8.8.8.8/openapi/callback",
                Set.of("RECORD_CREATED", "FLOW_STARTED"), "env://CALLBACK_SECRET", 3, 5),
                "request-1", "trace-1");

        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.configVersion()).isEqualTo(1);
        assertThat(created.signingSecretVersion()).isEqualTo(1);
        assertThat(created.endpoint()).isEqualTo("https://8.8.8.8/********");
        assertThat(created.secretRef()).isEqualTo("env://********");
        assertThat(callbacks.current.version().secretRef()).isEqualTo("env://CALLBACK_SECRET");
        assertThat(audits.successes).singleElement().satisfies(audit -> {
            assertThat(audit.requestId()).isEqualTo("request-1");
            assertThat(audit.after().toString()).doesNotContain("CALLBACK_SECRET", "8.8.8.8");
        });
    }

    @Test
    void rotatesSecretRefIntoNewImmutableVersionAndRetiresPreviousVersion() {
        var created = create();
        var rotated = service.rotateSecret(session, 30, Long.parseLong(created.id()),
                new OpenApiCallbackRequests.RotateSigningSecret("env://CALLBACK_SECRET_V2", 0L),
                "request-2", "trace-2");

        assertThat(rotated.configVersion()).isEqualTo(2);
        assertThat(rotated.signingSecretVersion()).isEqualTo(2);
        assertThat(rotated.secretRef()).isEqualTo("env://********");
        assertThat(callbacks.retired).singleElement().satisfies(version -> {
            assertThat(version.status()).isEqualTo(OpenApiCallbackVersion.Status.RETIRED);
            assertThat(version.retiredAt()).isEqualTo(NOW);
        });
        assertThat(callbacks.current.version().secretRef()).isEqualTo("env://CALLBACK_SECRET_V2");
    }

    @Test
    void disablesAndReenablesWithOptimisticVersion() {
        var created = create();
        var id = Long.parseLong(created.id());
        var disabled = service.disable(session, 30, id,
                new OpenApiCallbackRequests.ChangeStatus(0L, "maintenance"),
                "request-3", "trace-3");
        var enabled = service.enable(session, 30, id,
                new OpenApiCallbackRequests.ChangeStatus(1L, "resume"),
                "request-4", "trace-4");

        assertThat(disabled.status()).isEqualTo("DISABLED");
        assertThat(enabled.status()).isEqualTo("ACTIVE");
        assertThat(enabled.version()).isEqualTo(2);
    }

    @Test
    void tenantAndApplicationScopeHideForeignSubscription() {
        var created = create();
        var foreign = new OpenApiAdminSession(7, 10, 21, 40,
                Set.of(OpenApiAdminSession.MANAGE_PERMISSION));

        assertThatThrownBy(() -> service.detail(foreign, 30, Long.parseLong(created.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("OPENAPI_CALLBACK_NOT_FOUND");
        assertThat(service.list(session, 30)).hasSize(1);
    }

    private com.unique.examine.openapi.api.OpenApiCallbackViews.Subscription create() {
        return service.create(session, 30, new OpenApiCallbackRequests.Create(
                "ERP callback", "https://8.8.8.8/callback", Set.of("RECORD_CREATED"),
                "env://CALLBACK_SECRET", 3, 5), "request-1", "trace-1");
    }

    private static OpenApiRepository.ApplicationBundle application() {
        var app = new OpenApiApplication(30, 10, 20, 40, "abcdefghijklmnop", "ERP",
                OpenApiApplication.Status.ACTIVE, Set.of("record.read"), List.of(), 60, 1,
                NOW, 7, NOW, 7, 0);
        return new OpenApiRepository.ApplicationBundle(app, new OpenApiCredential(31, 30, 1,
                "env://API_SECRET", OpenApiCredential.Status.ACTIVE, NOW, null, NOW, 7));
    }

    private static final class SequentialIds extends IdService {
        private final AtomicLong ids = new AtomicLong(100);
        @Override public long nextId() { return ids.incrementAndGet(); }
    }

    private static final class Audits implements OperationAuditFacade {
        private final List<OperationAudit> successes = new ArrayList<>();
        @Override public void recordSuccess(OperationAudit audit) { successes.add(audit); }
        @Override public void recordDenied(OperationAudit audit) { throw new UnsupportedOperationException(); }
        @Override public void recordFailed(OperationAudit audit) { throw new UnsupportedOperationException(); }
    }

    private static final class Store implements OpenApiCallbackRepository {
        private Bundle current;
        private final List<OpenApiCallbackVersion> retired = new ArrayList<>();
        @Override public Optional<Bundle> find(long s, long t, long a, long id) {
            return current != null && current.subscription().systemId() == s
                    && current.subscription().tenantId() == t
                    && current.subscription().applicationId() == a
                    && current.subscription().id() == id ? Optional.of(current) : Optional.empty();
        }
        @Override public List<Bundle> list(long s, long t, long a) {
            return current == null ? List.of() : find(s, t, a, current.subscription().id()).stream().toList();
        }
        @Override public List<Bundle> listActiveForEvent(long s, long t, long a, String event) {
            return list(s, t, a).stream().filter(value -> value.version().eventTypes().contains(event)).toList();
        }
        @Override public void insert(OpenApiCallbackSubscription subscription, OpenApiCallbackVersion version) {
            current = new Bundle(subscription, version);
        }
        @Override public boolean replaceVersion(OpenApiCallbackSubscription updated,
                                                OpenApiCallbackVersion previous,
                                                OpenApiCallbackVersion replacement,
                                                long expectedVersion) {
            if (current.subscription().version() != expectedVersion) return false;
            retired.add(previous);
            current = new Bundle(updated, replacement);
            return true;
        }
        @Override public boolean changeStatus(long s, long t, long a, long id,
                                              OpenApiCallbackSubscription.Status status, long actor,
                                              Instant now, long expectedVersion) {
            if (find(s, t, a, id).isEmpty() || current.subscription().version() != expectedVersion) return false;
            var old = current.subscription();
            current = new Bundle(new OpenApiCallbackSubscription(old.id(), s, t, a, old.name(), status,
                    old.currentConfigVersion(), old.createdAt(), old.createdBy(), now, actor,
                    old.version() + 1), current.version());
            return true;
        }
        @Override public boolean insertDelivery(OpenApiCallbackDelivery delivery) { return false; }
        @Override public Optional<DeliveryBundle> findDelivery(long id) { return Optional.empty(); }
        @Override public boolean completeAttempt(OpenApiCallbackDelivery d, OpenApiCallbackAttempt a,
                                                 int count, long version) { return false; }
        @Override public List<OpenApiCallbackDelivery> listDeliveries(long s, long t, long a,
                                                                      long id, int o, int l) { return List.of(); }
        @Override public long countDeliveries(long s, long t, long a, long id) { return 0; }
    }

    private static final class Applications implements OpenApiRepository {
        private final ApplicationBundle app;
        private Applications(ApplicationBundle app) { this.app = app; }
        @Override public Optional<ApplicationBundle> findByAppKey(String key) { return Optional.empty(); }
        @Override public Optional<ApplicationBundle> find(long s, long t, long a) {
            return app.application().systemId() == s && app.application().tenantId() == t
                    && app.application().id() == a ? Optional.of(app) : Optional.empty();
        }
        @Override public List<ApplicationBundle> list(long s, long t, int o, int l) { return List.of(); }
        @Override public long count(long s, long t) { return 0; }
        @Override public void insertApplication(OpenApiApplication app) { throw unsupported(); }
        @Override public void insertCredential(OpenApiCredential credential) { throw unsupported(); }
        @Override public boolean updatePolicy(OpenApiApplication app, long version) { return false; }
        @Override public boolean updateStatus(long s, long t, long a, OpenApiApplication.Status status,
                                              long actor, Instant now, long version) { return false; }
        @Override public boolean rotateCredential(OpenApiApplication app, OpenApiCredential p,
                                                  OpenApiCredential r, long version) { return false; }
        @Override public boolean consumeNonce(long app, int version, String nonce, Instant e, Instant c) {
            return false;
        }
        @Override public OpenApiRateBucket lockRateBucket(long app, Instant window) { throw unsupported(); }
        @Override public boolean incrementRateBucket(long app, Instant window, int count, long version) {
            return false;
        }
        @Override public void insertCallLog(OpenApiCallLog log) { throw unsupported(); }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    }
}
