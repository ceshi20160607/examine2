package com.unique.examine.plat.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.plat.base.entity.Account;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
class JdbcIdentityRepository implements IdentityRepository {
    private static final String PROVIDER_COLUMNS = """
            id,provider_code,name,protocol,issuer_uri,authorization_endpoint,token_endpoint,
            jwks_uri,directory_endpoint,client_id,secret_ref,secret_version,callback_uri,scopes,
            allowed_domains_json,attribute_mapping_json,jit_account,jit_system_member,system_id,
            tenant_id,mfa_policy,status,preflight_status,preflight_version,preflight_failure_code,
            preflight_at,published_at,version
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcIdentityRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdentityApi.Provider> listProviders() {
        return jdbc.query("SELECT " + PROVIDER_COLUMNS
                + " FROM un_plat_identity_provider ORDER BY updated_at DESC,id DESC", this::provider);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityApi.Provider> findProvider(long id) {
        return jdbc.query("SELECT " + PROVIDER_COLUMNS
                        + " FROM un_plat_identity_provider WHERE id=?", this::provider, id)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityApi.Provider> findPublishedProvider(String code, Long systemId, Long tenantId) {
        if (systemId == null || tenantId == null) {
            return jdbc.query("SELECT " + PROVIDER_COLUMNS + " FROM un_plat_identity_provider "
                            + "WHERE provider_code=? AND status='PUBLISHED' AND system_id IS NULL "
                            + "AND tenant_id IS NULL LIMIT 1", this::provider, code).stream().findFirst();
        }
        return jdbc.query("""
                SELECT p.id,p.provider_code,p.name,p.protocol,p.issuer_uri,p.authorization_endpoint,
                  p.token_endpoint,p.jwks_uri,p.directory_endpoint,p.client_id,p.secret_ref,p.secret_version,
                  p.callback_uri,p.scopes,
                  CASE WHEN p.system_id IS NULL THEN pol.allowed_domains_json ELSE p.allowed_domains_json END
                    AS allowed_domains_json,
                  p.attribute_mapping_json,p.jit_account,
                  CASE WHEN p.system_id IS NULL THEN pol.jit_system_member ELSE p.jit_system_member END
                    AS jit_system_member,
                  COALESCE(p.system_id,pol.system_id) AS system_id,
                  COALESCE(p.tenant_id,pol.tenant_id) AS tenant_id,
                  p.mfa_policy,p.status,p.preflight_status,p.preflight_version,p.preflight_failure_code,
                  p.preflight_at,p.published_at,p.version
                FROM un_plat_identity_provider p
                LEFT JOIN un_plat_system_identity_policy pol
                  ON pol.provider_id=p.id AND pol.system_id=? AND pol.tenant_id=? AND pol.status='ACTIVE'
                WHERE p.provider_code=? AND p.status='PUBLISHED'
                  AND ((p.system_id=? AND p.tenant_id=?)
                    OR (p.system_id IS NULL AND p.tenant_id IS NULL AND pol.id IS NOT NULL))
                ORDER BY p.system_id IS NOT NULL DESC LIMIT 1
                """, this::provider, systemId, tenantId, code, systemId, tenantId).stream().findFirst();
    }

    @Override
    public boolean tenantExists(long systemId, long tenantId) {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant WHERE system_id=? AND id=? "
                + "AND status='ACTIVE' AND deleted_at IS NULL", Integer.class, systemId, tenantId);
        return count != null && count == 1;
    }

    @Override
    @Transactional
    public IdentityApi.Provider insertProvider(long id, IdentityApi.ProviderCommand c,
                                                long actorId, Instant now) {
        jdbc.update("""
                INSERT INTO un_plat_identity_provider(
                  id,provider_code,name,protocol,issuer_uri,authorization_endpoint,token_endpoint,
                  jwks_uri,directory_endpoint,client_id,secret_ref,secret_version,callback_uri,scopes,
                  allowed_domains_json,attribute_mapping_json,jit_account,jit_system_member,system_id,
                  tenant_id,mfa_policy,status,preflight_status,created_at,created_by,updated_at,updated_by,version)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT','NOT_RUN',?,?,?,?,0)
                """, id, c.providerCode(), c.name(), c.protocol().name(), c.issuerUri(),
                c.authorizationEndpoint(), c.tokenEndpoint(), c.jwksUri(), c.directoryEndpoint(),
                c.clientId(), c.secretRef(), c.secretVersion(), c.callbackUri(), c.scopes(),
                write(c.allowedDomains()), write(c.attributeMapping()), c.jitAccount(),
                c.jitSystemMember(), id(c.systemId()), id(c.tenantId()), c.mfaPolicy().name(),
                timestamp(now), actorId, timestamp(now), actorId);
        return findProvider(id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<IdentityApi.Provider> updateProvider(long id, IdentityApi.ProviderCommand c,
                                                          long actorId, Instant now) {
        var changed = jdbc.update("""
                UPDATE un_plat_identity_provider SET provider_code=?,name=?,protocol=?,issuer_uri=?,
                  authorization_endpoint=?,token_endpoint=?,jwks_uri=?,directory_endpoint=?,client_id=?,
                  secret_ref=?,secret_version=?,callback_uri=?,scopes=?,allowed_domains_json=?,
                  attribute_mapping_json=?,jit_account=?,jit_system_member=?,system_id=?,tenant_id=?,
                  mfa_policy=?,status='DRAFT',preflight_status='NOT_RUN',preflight_version=NULL,
                  preflight_failure_code=NULL,preflight_at=NULL,published_at=NULL,updated_at=?,updated_by=?,
                  version=version+1
                WHERE id=? AND version=?
                """, c.providerCode(), c.name(), c.protocol().name(), c.issuerUri(),
                c.authorizationEndpoint(), c.tokenEndpoint(), c.jwksUri(), c.directoryEndpoint(),
                c.clientId(), c.secretRef(), c.secretVersion(), c.callbackUri(), c.scopes(),
                write(c.allowedDomains()), write(c.attributeMapping()), c.jitAccount(),
                c.jitSystemMember(), id(c.systemId()), id(c.tenantId()), c.mfaPolicy().name(),
                timestamp(now), actorId, id, c.expectedVersion());
        return changed == 1 ? findProvider(id) : Optional.empty();
    }

    @Override
    @Transactional
    public Optional<IdentityApi.Provider> recordPreflight(long id, long expectedVersion,
                                                           boolean passed, String failureCode, Instant now) {
        var changed = jdbc.update("""
                UPDATE un_plat_identity_provider SET preflight_status=?,preflight_version=version,
                  preflight_failure_code=?,preflight_at=?,updated_at=?
                WHERE id=? AND version=? AND status<>'DISABLED'
                """, passed ? "PASSED" : "FAILED", failureCode, timestamp(now), timestamp(now),
                id, expectedVersion);
        return changed == 1 ? findProvider(id) : Optional.empty();
    }

    @Override
    @Transactional
    public Optional<IdentityApi.Provider> transitionProvider(long id, long expectedVersion,
                                                              IdentityApi.Status status,
                                                              long actorId, Instant now) {
        String guard = status == IdentityApi.Status.PUBLISHED
                ? " AND preflight_status='PASSED' AND preflight_version=version" : "";
        String sql = "UPDATE un_plat_identity_provider SET status=?,published_at=?,disabled_at=?,"
                + "updated_at=?,updated_by=?,version=version+1 WHERE id=? AND version=?" + guard;
        var changed = jdbc.update(sql, status.name(), status == IdentityApi.Status.PUBLISHED ? timestamp(now) : null,
                status == IdentityApi.Status.DISABLED ? timestamp(now) : null,
                timestamp(now), actorId, id, expectedVersion);
        return changed == 1 ? findProvider(id) : Optional.empty();
    }

    @Override
    @Transactional
    public void createAuthState(long id, String stateHash, IdentityApi.Provider provider,
                                Long systemId, Long tenantId, String redirectUri, String nonce,
                                String verifier, Instant expiresAt, Instant now) {
        jdbc.update("""
                INSERT INTO un_plat_identity_auth_state(
                  id,state_hash,provider_id,system_id,tenant_id,redirect_uri,nonce,code_verifier,
                  status,expires_at,created_at)
                VALUES(?,?,?,?,?,?,?,?,'ACTIVE',?,?)
                """, id, stateHash, provider.id(), systemId, tenantId, redirectUri, nonce, verifier,
                timestamp(expiresAt), timestamp(now));
    }

    @Override
    @Transactional
    public Optional<IdentityApi.AuthState> consumeAuthState(String stateHash, Instant now) {
        var rows = jdbc.query("""
                SELECT id,provider_id,system_id,tenant_id,redirect_uri,nonce,code_verifier,expires_at
                FROM un_plat_identity_auth_state
                WHERE state_hash=? AND status='ACTIVE' AND expires_at>?
                """, (r, row) -> new StateRow(r.getLong("id"), r.getLong("provider_id"),
                nullableLong(r, "system_id"), nullableLong(r, "tenant_id"), r.getString("redirect_uri"),
                r.getString("nonce"), r.getString("code_verifier"), instant(r, "expires_at")),
                stateHash, timestamp(now));
        if (rows.size() != 1) return Optional.empty();
        var value = rows.getFirst();
        if (jdbc.update("UPDATE un_plat_identity_auth_state SET status='CONSUMED',consumed_at=? "
                + "WHERE id=? AND status='ACTIVE'", timestamp(now), value.id()) != 1) return Optional.empty();
        var provider = findProvider(value.providerId()).orElseThrow();
        return Optional.of(new IdentityApi.AuthState(value.id(), provider, value.systemId(),
                value.tenantId(), value.redirectUri(), value.nonce(), value.verifier(), value.expiresAt()));
    }

    @Override
    public Optional<Long> findBoundAccount(long providerId, String externalUserId,
                                           Long systemId, Long tenantId) {
        return jdbc.query("SELECT account_id FROM un_plat_identity_binding WHERE provider_id=? "
                        + "AND external_user_id=? AND status='ACTIVE' AND system_id<=>? AND tenant_id<=>?",
                (r, row) -> r.getLong(1), providerId, externalUserId, systemId, tenantId)
                .stream().findFirst();
    }

    @Override
    public Optional<Long> findAccountByEmail(String normalizedEmail) {
        return jdbc.query("SELECT id FROM un_plat_account WHERE email_normalized=? AND status='ACTIVE' "
                        + "AND deleted_at IS NULL", (r, row) -> r.getLong(1), normalizedEmail)
                .stream().findFirst();
    }

    @Override
    public Account account(long accountId) {
        return jdbc.query("SELECT id,account_code,username,username_normalized,email,email_normalized,phone,"
                        + "display_name,locale,time_zone,status,last_login_at,created_at,created_by,updated_at,"
                        + "updated_by,version FROM un_plat_account WHERE id=? AND deleted_at IS NULL",
                this::mapAccount, accountId).stream().findFirst().orElseThrow();
    }

    @Override
    public void insertJitAccount(Account a) {
        jdbc.update("""
                INSERT INTO un_plat_account(id,account_code,username,username_normalized,email,email_normalized,
                  phone,display_name,locale,time_zone,status,created_at,created_by,updated_at,updated_by,version)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                """, a.getId(), a.getAccountCode(), a.getUsername(), a.getUsernameNormalized(), a.getEmail(),
                a.getEmailNormalized(), a.getPhone(), a.getDisplayName(), a.getLocale(), a.getTimeZone(),
                a.getStatus(), a.getCreatedAt(), a.getCreatedBy(), a.getUpdatedAt(), a.getUpdatedBy());
    }

    @Override
    public Optional<Long> findMember(long systemId, long tenantId, long accountId) {
        return jdbc.query("""
                SELECT m.id FROM un_plat_member m JOIN un_plat_member_tenant mt
                  ON mt.system_id=m.system_id AND mt.member_id=m.id
                WHERE m.system_id=? AND mt.tenant_id=? AND m.account_id=?
                  AND m.status='ACTIVE' AND mt.status='ACTIVE' AND m.deleted_at IS NULL AND mt.deleted_at IS NULL
                """, (r, row) -> r.getLong(1), systemId, tenantId, accountId).stream().findFirst();
    }

    @Override
    @Transactional
    public long insertJitMember(long id, long tenantAccessId, long systemId, long tenantId,
                                long accountId, String memberCode, String displayName, Instant now) {
        var t = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO un_plat_member(id,system_id,account_id,member_code,display_name,default_tenant_id,
                  status,joined_at,created_at,created_by,updated_at,updated_by,version)
                VALUES(?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,0)
                """, id, systemId, accountId, memberCode, displayName, tenantId, t, t, accountId, t, accountId);
        jdbc.update("""
                INSERT INTO un_plat_member_tenant(id,system_id,member_id,tenant_id,status,granted_at,granted_by,
                  created_at,created_by,updated_at,updated_by,version)
                VALUES(?,?,?,?,'ACTIVE',?,?,?,?,?,?,0)
                """, tenantAccessId, systemId, id, tenantId, t, accountId, t, accountId, t, accountId);
        return id;
    }

    @Override
    public void bindIdentity(long id, IdentityApi.Provider p, IdentityApi.ExternalIdentity identity,
                             long accountId, Long memberId, Instant now) {
        try {
            jdbc.update("""
                    INSERT INTO un_plat_identity_binding(id,provider_id,external_user_id,account_id,system_id,
                      tenant_id,member_id,email_normalized,external_department_id,attributes_json,status,
                      last_login_at,created_at,updated_at,version)
                    VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?,0)
                    """, id, p.id(), identity.subject(), accountId, p.systemId(), p.tenantId(), memberId,
                    normalized(identity.email()), identity.departmentId(), write(identity.claims()), timestamp(now),
                    timestamp(now), timestamp(now));
        } catch (DuplicateKeyException duplicate) {
            var existing = findBoundAccount(p.id(), identity.subject(), p.systemId(), p.tenantId());
            if (existing.isEmpty() || existing.get() != accountId) throw duplicate;
            jdbc.update("UPDATE un_plat_identity_binding SET member_id=COALESCE(?,member_id),"
                    + "external_department_id=?,attributes_json=?,last_login_at=?,updated_at=?,version=version+1 "
                    + "WHERE provider_id=? AND external_user_id=? AND account_id=?",
                    memberId, identity.departmentId(), write(identity.claims()), timestamp(now), timestamp(now),
                    p.id(), identity.subject(), accountId);
        }
    }

    @Override
    public void touchBinding(long providerId, String externalUserId, Instant now) {
        jdbc.update("UPDATE un_plat_identity_binding SET last_login_at=?,updated_at=?,version=version+1 "
                + "WHERE provider_id=? AND external_user_id=? AND status='ACTIVE'",
                timestamp(now), timestamp(now), providerId, externalUserId);
    }

    @Override
    public Optional<IdentityApi.MfaEnrollment> findEnrollment(long accountId, Long systemId, Long tenantId) {
        return jdbc.query("""
                SELECT id,account_id,system_id,tenant_id,secret_ref,secret_version,last_used_step,verified_at,version
                FROM un_plat_mfa_enrollment WHERE account_id=? AND system_id<=>? AND tenant_id<=>? AND status='ACTIVE'
                """, this::enrollment, accountId, systemId, tenantId).stream().findFirst();
    }

    @Override
    @Transactional
    public IdentityApi.MfaEnrollment upsertEnrollment(long id, long accountId, Long systemId,
                                                       Long tenantId, String secretRef,
                                                       String secretVersion, Instant now) {
        jdbc.update("""
                INSERT INTO un_plat_mfa_enrollment(id,account_id,system_id,tenant_id,secret_ref,secret_version,
                  status,verified_at,created_at,updated_at,version)
                VALUES(?,?,?,?,?,?,'ACTIVE',?,?,?,0)
                ON DUPLICATE KEY UPDATE secret_ref=VALUES(secret_ref),secret_version=VALUES(secret_version),
                  status='ACTIVE',verified_at=VALUES(verified_at),last_used_step=NULL,updated_at=VALUES(updated_at),
                  version=version+1
                """, id, accountId, systemId, tenantId, secretRef, secretVersion,
                timestamp(now), timestamp(now), timestamp(now));
        return findEnrollment(accountId, systemId, tenantId).orElseThrow();
    }

    @Override
    @Transactional
    public void replaceRecoveryCodes(long enrollmentId, List<RecoveryCodeHash> hashes, Instant now) {
        jdbc.update("UPDATE un_plat_mfa_recovery_code SET status='REVOKED' "
                + "WHERE enrollment_id=? AND status='ACTIVE'", enrollmentId);
        for (var value : hashes) {
            jdbc.update("INSERT INTO un_plat_mfa_recovery_code(id,enrollment_id,code_hash,status,created_at) "
                    + "VALUES(?,?,?,'ACTIVE',?)", value.id(), enrollmentId, value.hash(), timestamp(now));
        }
        jdbc.update("UPDATE un_plat_mfa_enrollment SET recovery_regenerated_at=?,updated_at=?,version=version+1 "
                + "WHERE id=?", timestamp(now), timestamp(now), enrollmentId);
    }

    @Override
    public boolean consumeRecoveryCode(long enrollmentId, String hash, Instant now) {
        return jdbc.update("UPDATE un_plat_mfa_recovery_code SET status='USED',used_at=? "
                        + "WHERE enrollment_id=? AND code_hash=? AND status='ACTIVE'",
                timestamp(now), enrollmentId, hash) == 1;
    }

    @Override
    public boolean advanceTotpStep(long enrollmentId, long expectedVersion, Long previousStep,
                                   long newStep, Instant now) {
        return jdbc.update("UPDATE un_plat_mfa_enrollment SET last_used_step=?,updated_at=?,version=version+1 "
                        + "WHERE id=? AND version=? AND last_used_step<=>? AND (last_used_step IS NULL OR last_used_step<?)",
                newStep, timestamp(now), enrollmentId, expectedVersion, previousStep, newStep) == 1;
    }

    @Override
    public void createMfaChallenge(long id, String hash, IdentityApi.Provider p, long accountId,
                                   Long systemId, Long tenantId, Instant expiresAt, Instant now) {
        jdbc.update("""
                INSERT INTO un_plat_mfa_challenge(id,challenge_hash,provider_id,account_id,system_id,tenant_id,
                  auth_method,status,expires_at,created_at)
                VALUES(?,?,?,?,?,?,'SSO','ACTIVE',?,?)
                """, id, hash, p.id(), accountId, systemId, tenantId, timestamp(expiresAt), timestamp(now));
    }

    @Override
    @Transactional
    public Optional<IdentityApi.MfaChallenge> consumeMfaChallenge(String hash, Instant now) {
        var rows = jdbc.query("SELECT id,provider_id,account_id,system_id,tenant_id,expires_at "
                        + "FROM un_plat_mfa_challenge WHERE challenge_hash=? AND status='ACTIVE' AND expires_at>?",
                (r, row) -> new ChallengeRow(r.getLong("id"), r.getLong("provider_id"),
                        r.getLong("account_id"), nullableLong(r, "system_id"), nullableLong(r, "tenant_id"),
                        instant(r, "expires_at")), hash, timestamp(now));
        if (rows.size() != 1) return Optional.empty();
        var row = rows.getFirst();
        if (jdbc.update("UPDATE un_plat_mfa_challenge SET status='CONSUMED',consumed_at=? "
                + "WHERE id=? AND status='ACTIVE'", timestamp(now), row.id()) != 1) return Optional.empty();
        return Optional.of(new IdentityApi.MfaChallenge(row.id(), findProvider(row.providerId()).orElseThrow(),
                row.accountId(), row.systemId(), row.tenantId(), row.expiresAt()));
    }

    private IdentityApi.Provider provider(ResultSet r, int row) throws SQLException {
        return new IdentityApi.Provider(r.getLong("id"), r.getString("provider_code"), r.getString("name"),
                IdentityApi.Protocol.valueOf(r.getString("protocol")), r.getString("issuer_uri"),
                r.getString("authorization_endpoint"), r.getString("token_endpoint"), r.getString("jwks_uri"),
                r.getString("directory_endpoint"), r.getString("client_id"), r.getString("secret_ref"),
                r.getString("secret_version"), r.getString("callback_uri"), r.getString("scopes"),
                read(r.getString("allowed_domains_json"), new TypeReference<List<String>>() {}),
                read(r.getString("attribute_mapping_json"), new TypeReference<Map<String, String>>() {}),
                r.getBoolean("jit_account"), r.getBoolean("jit_system_member"),
                nullableLong(r, "system_id"), nullableLong(r, "tenant_id"),
                IdentityApi.MfaPolicy.valueOf(r.getString("mfa_policy")),
                IdentityApi.Status.valueOf(r.getString("status")), r.getString("preflight_status"),
                nullableLong(r, "preflight_version"), r.getString("preflight_failure_code"),
                instant(r, "preflight_at"), instant(r, "published_at"), r.getLong("version"));
    }

    private Account mapAccount(ResultSet r, int row) throws SQLException {
        var a = new Account();
        a.setId(r.getLong("id")); a.setAccountCode(r.getString("account_code"));
        a.setUsername(r.getString("username")); a.setUsernameNormalized(r.getString("username_normalized"));
        a.setEmail(r.getString("email")); a.setEmailNormalized(r.getString("email_normalized"));
        a.setPhone(r.getString("phone")); a.setDisplayName(r.getString("display_name"));
        a.setLocale(r.getString("locale")); a.setTimeZone(r.getString("time_zone"));
        a.setStatus(r.getString("status")); a.setLastLoginAt(local(r, "last_login_at"));
        a.setCreatedAt(local(r, "created_at")); a.setCreatedBy(nullableLong(r, "created_by"));
        a.setUpdatedAt(local(r, "updated_at")); a.setUpdatedBy(nullableLong(r, "updated_by"));
        a.setVersion(r.getLong("version"));
        return a;
    }

    private IdentityApi.MfaEnrollment enrollment(ResultSet r, int row) throws SQLException {
        return new IdentityApi.MfaEnrollment(r.getLong("id"), r.getLong("account_id"),
                nullableLong(r, "system_id"), nullableLong(r, "tenant_id"), r.getString("secret_ref"),
                r.getString("secret_version"), nullableLong(r, "last_used_step"), instant(r, "verified_at"),
                r.getLong("version"));
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Identity configuration is not JSON", e); }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored identity JSON is invalid", e); }
    }

    private static Long id(String value) { return value == null || value.isBlank() ? null : Long.parseLong(value); }
    private static String normalized(String value) { return value == null ? null : value.strip().toLowerCase(java.util.Locale.ROOT); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet r, String name) throws SQLException {
        var value = r.getTimestamp(name); return value == null ? null : value.toInstant();
    }
    private static LocalDateTime local(ResultSet r, String name) throws SQLException {
        var value = r.getTimestamp(name); return value == null ? null : value.toLocalDateTime();
    }
    private static Long nullableLong(ResultSet r, String name) throws SQLException {
        long value = r.getLong(name); return r.wasNull() ? null : value;
    }

    private record StateRow(long id, long providerId, Long systemId, Long tenantId, String redirectUri,
                            String nonce, String verifier, Instant expiresAt) { }
    private record ChallengeRow(long id, long providerId, long accountId, Long systemId, Long tenantId,
                                Instant expiresAt) { }
}
