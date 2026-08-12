package com.unique.examine.event.adapter.jdbc;

import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.EventChannelTargetDirectory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.util.Optional;

/** Resolves active, tenant-scoped member mailboxes and enabled system webhook targets. */
public final class JdbcEventChannelTargetDirectory implements EventChannelTargetDirectory {
    static final String EMAIL_SQL = """
            SELECT account.email
              FROM un_event_channel_configuration config
              JOIN un_plat_member member
                ON member.system_id = config.system_id AND member.id = ?
              JOIN un_plat_member_tenant membership
                ON membership.system_id = member.system_id
               AND membership.member_id = member.id AND membership.tenant_id = ?
              JOIN un_plat_account account ON account.id = member.account_id
             WHERE config.system_id = ? AND config.channel = 'EMAIL' AND config.enabled = TRUE
               AND member.status = 'ACTIVE' AND member.deleted_at IS NULL
               AND membership.status = 'ACTIVE' AND membership.deleted_at IS NULL
               AND (membership.expires_at IS NULL OR membership.expires_at > CURRENT_TIMESTAMP(3))
               AND account.status = 'ACTIVE' AND account.deleted_at IS NULL
               AND account.email IS NOT NULL AND account.email <> ''
            """;
    static final String WEBHOOK_SQL = """
            SELECT endpoint, secret_ref, timeout_ms
              FROM un_event_channel_configuration
             WHERE system_id = ? AND channel = 'WEBHOOK' AND enabled = TRUE
               AND endpoint IS NOT NULL AND secret_ref IS NOT NULL
            """;

    private final JdbcTemplate jdbc;

    public JdbcEventChannelTargetDirectory(JdbcTemplate jdbc) {
        if (jdbc == null) throw new IllegalArgumentException("JdbcTemplate is required");
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Target> resolve(DeliveryChannel channel, long systemId, long tenantId,
                                    long recipientMemberId) {
        if (channel == DeliveryChannel.EMAIL) {
            return jdbc.query(EMAIL_SQL,
                    (row, number) -> new Target(row.getString("email"), null, null, null),
                    recipientMemberId, tenantId, systemId).stream().findFirst();
        }
        if (channel == DeliveryChannel.WEBHOOK) {
            return jdbc.query(WEBHOOK_SQL,
                    (row, number) -> new Target(null, URI.create(row.getString("endpoint")),
                            row.getString("secret_ref"), row.getInt("timeout_ms")),
                    systemId).stream().findFirst();
        }
        return Optional.empty();
    }
}
