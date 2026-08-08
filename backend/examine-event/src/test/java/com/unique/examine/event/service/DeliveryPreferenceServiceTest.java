package com.unique.examine.event.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.support.TestDeliveryPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryPreferenceServiceTest {
    private TestDeliveryPreferenceRepository repository;
    private DeliveryPreferenceService service;

    @BeforeEach
    void setUp() {
        repository = new TestDeliveryPreferenceRepository();
        service = new DeliveryPreferenceService(repository, new CatalogTemplateService(),
                Clock.fixed(Instant.parse("2026-08-05T08:30:00Z"), ZoneOffset.UTC));
    }

    @Test
    void implicitDefaultsAreSafeOrderedAndRepeatReadsDoNotWrite() {
        var views = service.list(actor(10, 20, 101));

        assertThat(views).extracting(DeliveryPreferenceService.PreferenceView::templateCode)
                .containsExactly("MODULE_EXPORT_FAILED", "MODULE_EXPORT_SUCCEEDED");
        assertThat(views).allSatisfy(view -> {
            assertThat(view.channel()).isEqualTo("INBOX");
            assertThat(view.enabled()).isTrue();
            assertThat(view.version()).isZero();
            assertThat(view.updatedAt()).isNull();
        });
        assertThat(views.getFirst().name()).isEqualTo("Export failed");
        assertThat(repository.writes()).isZero();

        assertThat(service.list(actor(10, 20, 101))).isEqualTo(views);
        assertThat(repository.writes()).isZero();
    }

    @Test
    void disableNoOpAndEnableUseExactCasVersions() {
        var disabled = service.update(actor(10, 20, 101),
                "MODULE_EXPORT_SUCCEEDED", false, 0L);
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.version()).isEqualTo(1);
        assertThat(disabled.updatedAt()).isEqualTo(Instant.parse("2026-08-05T08:30:00Z"));
        assertThat(repository.writes()).isEqualTo(1);

        var noOp = service.update(actor(10, 20, 101),
                "MODULE_EXPORT_SUCCEEDED", false, 1L);
        assertThat(noOp).isEqualTo(disabled);
        assertThat(repository.writes()).isEqualTo(1);

        assertThatThrownBy(() -> service.update(actor(10, 20, 101),
                "MODULE_EXPORT_SUCCEEDED", true, 0L))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "EVENT_DELIVERY_PREFERENCE_VERSION_CONFLICT"));
        assertThat(repository.writes()).isEqualTo(1);

        var enabled = service.update(actor(10, 20, 101),
                "MODULE_EXPORT_SUCCEEDED", true, 1L);
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.version()).isEqualTo(2);
        assertThat(repository.writes()).isEqualTo(2);
    }

    @Test
    void overridesAreIsolatedByTenantAndMember() {
        service.update(actor(10, 20, 101), "MODULE_EXPORT_SUCCEEDED", false, 0L);

        assertThat(preference(actor(10, 20, 101)).enabled()).isFalse();
        assertThat(preference(actor(10, 21, 101)).enabled()).isTrue();
        assertThat(preference(actor(10, 20, 102)).enabled()).isTrue();
    }

    @Test
    void exactCodesBodiesVersionsAndAccessFailClosed() {
        assertCode(() -> service.update(actor(10, 20, 101), "module_export_succeeded", false, 0L),
                "EVENT_DELIVERY_PREFERENCE_INVALID");
        assertCode(() -> service.update(actor(10, 20, 101), " MODULE_EXPORT_SUCCEEDED", false, 0L),
                "EVENT_DELIVERY_PREFERENCE_INVALID");
        assertCode(() -> service.update(actor(10, 20, 101), "UNKNOWN_TEMPLATE", false, 0L),
                "MESSAGE_TEMPLATE_NOT_FOUND");
        assertCode(() -> service.update(actor(10, 20, 101), "MODULE_EXPORT_SUCCEEDED", null, 0L),
                "EVENT_DELIVERY_PREFERENCE_INVALID");
        assertCode(() -> service.update(actor(10, 20, 101), "MODULE_EXPORT_SUCCEEDED", false, -1L),
                "EVENT_DELIVERY_PREFERENCE_INVALID");
        assertCode(() -> service.list(new EventActor(10, 20, 101, Set.of())),
                "EVENT_DELIVERY_PREFERENCE_FORBIDDEN");
    }

    @Test
    void declaredChannelsExpandInStableOrderAndUpdatesStayChannelScoped() {
        service = new DeliveryPreferenceService(repository, new MultiChannelCatalogTemplateService(),
                Clock.fixed(Instant.parse("2026-08-05T08:30:00Z"), ZoneOffset.UTC));

        var views = service.list(actor(10, 20, 101));
        assertThat(views).extracting(value -> value.templateCode() + ':' + value.channel())
                .containsExactly(
                        "MODULE_EXPORT_FAILED:INBOX",
                        "MODULE_EXPORT_SUCCEEDED:INBOX",
                        "MODULE_EXPORT_SUCCEEDED:EMAIL",
                        "MODULE_EXPORT_SUCCEEDED:WEBHOOK");

        var disabled = service.update(actor(10, 20, 101), "MODULE_EXPORT_SUCCEEDED",
                "EMAIL", false, 0L);
        assertThat(disabled.channel()).isEqualTo("EMAIL");
        assertThat(disabled.enabled()).isFalse();
        assertThat(service.list(actor(10, 20, 101))).filteredOn(value ->
                        value.templateCode().equals("MODULE_EXPORT_SUCCEEDED"))
                .extracting(DeliveryPreferenceService.PreferenceView::enabled)
                .containsExactly(true, false, true);

        assertCode(() -> service.update(actor(10, 20, 101), "MODULE_EXPORT_FAILED",
                        "EMAIL", false, 0L),
                "EVENT_DELIVERY_PREFERENCE_INVALID");
    }

    private DeliveryPreferenceService.PreferenceView preference(EventActor actor) {
        return service.list(actor).stream().filter(view ->
                view.templateCode().equals("MODULE_EXPORT_SUCCEEDED")).findFirst().orElseThrow();
    }

    private static EventActor actor(long systemId, long tenantId, long memberId) {
        return new EventActor(systemId, tenantId, memberId, Set.of(DeliveryPreferenceService.ACCESS));
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }

    private static final class CatalogTemplateService extends MessageTemplateService {
        private CatalogTemplateService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<PreferenceTemplate> preferenceCatalog(long systemId, long actorId) {
            return List.of(
                    new PreferenceTemplate("MODULE_EXPORT_FAILED", "MODULE_EXPORT_FAILED", "Export failed"),
                    new PreferenceTemplate("MODULE_EXPORT_SUCCEEDED", "MODULE_EXPORT_SUCCEEDED", "Export done"));
        }

        @Override
        public PreferenceTemplate requirePreferenceTemplate(long systemId, long actorId, String code) {
            return preferenceCatalog(systemId, actorId).stream()
                    .filter(value -> value.templateCode().equals(code))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("MESSAGE_TEMPLATE_NOT_FOUND",
                            "Message template was not found", org.springframework.http.HttpStatus.NOT_FOUND));
        }
    }

    private static final class MultiChannelCatalogTemplateService extends MessageTemplateService {
        private MultiChannelCatalogTemplateService() { super(null, null, null, null, null); }

        @Override
        public List<PreferenceTemplate> preferenceCatalog(long systemId, long actorId) {
            return List.of(
                    new PreferenceTemplate("MODULE_EXPORT_FAILED", "MODULE_EXPORT_FAILED", "Export failed",
                            List.of(DeliveryChannel.INBOX)),
                    new PreferenceTemplate("MODULE_EXPORT_SUCCEEDED", "MODULE_EXPORT_SUCCEEDED", "Export done",
                            DeliveryChannel.STABLE_ORDER));
        }

        @Override
        public PreferenceTemplate requirePreferenceTemplate(long systemId, long actorId, String code) {
            return preferenceCatalog(systemId, actorId).stream()
                    .filter(value -> value.templateCode().equals(code)).findFirst()
                    .orElseThrow(() -> new BusinessException("MESSAGE_TEMPLATE_NOT_FOUND",
                            "Message template was not found", org.springframework.http.HttpStatus.NOT_FOUND));
        }
    }
}
