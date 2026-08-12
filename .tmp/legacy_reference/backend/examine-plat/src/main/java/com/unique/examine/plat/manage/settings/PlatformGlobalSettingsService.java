package com.unique.examine.plat.manage.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.ClientRequest;
import com.unique.examine.plat.manage.service.PlatformMutationSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PlatformGlobalSettingsService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PlatformMutationSupport mutations;

    public PlatformGlobalSettingsService(
            JdbcTemplate jdbc, ObjectMapper json, PlatformMutationSupport mutations
    ) {
        this.jdbc = jdbc;
        this.json = json;
        this.mutations = mutations;
    }

    public PlatformGlobalSettingsModels.Settings get() {
        try {
            return jdbc.queryForObject("SELECT * FROM un_plat_global_setting WHERE id=1", (row, ignored) ->
                    new PlatformGlobalSettingsModels.Settings(
                            read(row.getString("profile_json"), PlatformGlobalSettingsModels.Profile.class),
                            read(row.getString("storage_policy_json"), PlatformGlobalSettingsModels.StoragePolicy.class),
                            read(row.getString("security_policy_json"), PlatformGlobalSettingsModels.SecurityPolicy.class),
                            read(row.getString("quota_policy_json"), PlatformGlobalSettingsModels.QuotaPolicy.class),
                            read(row.getString("backup_policy_json"), PlatformGlobalSettingsModels.BackupPolicy.class),
                            read(row.getString("release_policy_json"), PlatformGlobalSettingsModels.ReleasePolicy.class),
                            Long.toString(row.getLong("version")), row.getTimestamp("updated_at").toLocalDateTime()));
        } catch (EmptyResultDataAccessException ignored) {
            return defaults();
        }
    }

    @Transactional
    public PlatformGlobalSettingsModels.Settings update(
            AuthenticatedSession session,
            PlatformGlobalSettingsModels.Update raw,
            ClientRequest client
    ) {
        var command = validate(raw);
        var expected = PlatformMutationSupport.version(command.expectedVersion());
        var before = get();
        if (Long.parseLong(before.version()) != expected) throw PlatformMutationSupport.versionConflict();
        var now = LocalDateTime.now();
        int changed;
        if (expected == 0 && before.updatedAt() == null) {
            changed = jdbc.update("""
                    INSERT INTO un_plat_global_setting(
                      id,profile_json,storage_policy_json,security_policy_json,quota_policy_json,
                      backup_policy_json,release_policy_json,updated_at,updated_by,version)
                    VALUES(1,CAST(? AS JSON),CAST(? AS JSON),CAST(? AS JSON),CAST(? AS JSON),
                      CAST(? AS JSON),CAST(? AS JSON),?,?,1)
                    """, write(command.profile()), write(command.storage()), write(command.security()),
                    write(command.quota()), write(command.backup()), write(command.release()),
                    now, session.accountId());
        } else {
            changed = jdbc.update("""
                    UPDATE un_plat_global_setting
                       SET profile_json=CAST(? AS JSON),storage_policy_json=CAST(? AS JSON),
                           security_policy_json=CAST(? AS JSON),quota_policy_json=CAST(? AS JSON),
                           backup_policy_json=CAST(? AS JSON),release_policy_json=CAST(? AS JSON),
                           updated_at=?,updated_by=?,version=version+1
                     WHERE id=1 AND version=?
                    """, write(command.profile()), write(command.storage()), write(command.security()),
                    write(command.quota()), write(command.backup()), write(command.release()),
                    now, session.accountId(), expected);
        }
        if (changed != 1) throw PlatformMutationSupport.versionConflict();
        var after = get();
        mutations.success(session, client, "PLATFORM_GLOBAL_SETTING", "1",
                "PLATFORM_GLOBAL_SETTINGS_UPDATED", before, after, null);
        return after;
    }

    private static PlatformGlobalSettingsModels.Update validate(PlatformGlobalSettingsModels.Update value) {
        if (value == null || value.profile() == null || value.storage() == null
                || value.security() == null || value.quota() == null
                || value.backup() == null || value.release() == null) {
            throw PlatformMutationSupport.validation("all platform setting groups are required");
        }
        var profile = new PlatformGlobalSettingsModels.Profile(
                text(value.profile().name(), "profile.name", 80, true),
                text(value.profile().description(), "profile.description", 500, false));
        var mode = upper(value.storage().defaultMode());
        if (!java.util.Set.of("LOCAL", "S3").contains(mode)) invalid("storage.defaultMode");
        range(value.storage().maximumUploadBytes(), 1_048_576L, 5_368_709_120L, "storage.maximumUploadBytes");
        range(value.storage().retentionDays(), 1, 3650, "storage.retentionDays");
        range(value.security().sessionIdleMinutes(), 5, 1440, "security.sessionIdleMinutes");
        range(value.security().passwordMinimumLength(), 8, 128, "security.passwordMinimumLength");
        range(value.quota().defaultMemberLimit(), 1, 1_000_000, "quota.defaultMemberLimit");
        range(value.quota().defaultModuleLimit(), 1, 10_000, "quota.defaultModuleLimit");
        range(value.quota().defaultStorageBytes(), 1_048_576L, 10_995_116_277_760L, "quota.defaultStorageBytes");
        range(value.backup().retentionDays(), 1, 3650, "backup.retentionDays");
        range(value.backup().intervalHours(), 1, 168, "backup.intervalHours");
        var channel = upper(value.release().channel());
        if (!java.util.Set.of("STABLE", "CANARY").contains(channel)) invalid("release.channel");
        return new PlatformGlobalSettingsModels.Update(
                profile,
                new PlatformGlobalSettingsModels.StoragePolicy(mode,
                        value.storage().maximumUploadBytes(), value.storage().retentionDays()),
                value.security(), value.quota(), value.backup(),
                new PlatformGlobalSettingsModels.ReleasePolicy(
                        value.release().maintenanceMode(), channel, value.release().approvalRequired()),
                value.expectedVersion());
    }

    private static PlatformGlobalSettingsModels.Settings defaults() {
        return new PlatformGlobalSettingsModels.Settings(
                new PlatformGlobalSettingsModels.Profile("Examine", ""),
                new PlatformGlobalSettingsModels.StoragePolicy("LOCAL", 20_971_520L, 365),
                new PlatformGlobalSettingsModels.SecurityPolicy(30, 12, true),
                new PlatformGlobalSettingsModels.QuotaPolicy(1000, 200, 107_374_182_400L),
                new PlatformGlobalSettingsModels.BackupPolicy(false, 30, 24),
                new PlatformGlobalSettingsModels.ReleasePolicy(false, "STABLE", true),
                "0", null);
    }

    private static String text(String value, String field, int maximum, boolean required) {
        if (value == null) value = "";
        var result = value.trim();
        if ((required && result.isEmpty()) || result.length() > maximum) invalid(field);
        return result;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void range(long value, long minimum, long maximum, String field) {
        if (value < minimum || value > maximum) invalid(field);
    }

    private static void invalid(String field) {
        throw PlatformMutationSupport.validation(field + " is invalid");
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("platform settings serialization failed", failure);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("platform settings persistence is invalid", failure);
        }
    }
}
