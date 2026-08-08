package com.unique.examine.plat.identity;

import com.unique.examine.plat.base.entity.Account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface IdentityRepository {
    List<IdentityApi.Provider> listProviders();
    Optional<IdentityApi.Provider> findProvider(long id);
    Optional<IdentityApi.Provider> findPublishedProvider(String code, Long systemId, Long tenantId);
    boolean tenantExists(long systemId, long tenantId);
    IdentityApi.Provider insertProvider(long id, IdentityApi.ProviderCommand command, long actorId, Instant now);
    Optional<IdentityApi.Provider> updateProvider(long id, IdentityApi.ProviderCommand command, long actorId, Instant now);
    Optional<IdentityApi.Provider> recordPreflight(long id, long expectedVersion, boolean passed,
                                                    String failureCode, Instant now);
    Optional<IdentityApi.Provider> transitionProvider(long id, long expectedVersion,
                                                       IdentityApi.Status status, long actorId, Instant now);

    void createAuthState(long id, String stateHash, IdentityApi.Provider provider, Long systemId,
                         Long tenantId, String redirectUri, String nonce, String codeVerifier,
                         Instant expiresAt, Instant now);
    Optional<IdentityApi.AuthState> consumeAuthState(String stateHash, Instant now);

    Optional<Long> findBoundAccount(long providerId, String externalUserId, Long systemId, Long tenantId);
    Optional<Long> findAccountByEmail(String normalizedEmail);
    Account account(long accountId);
    void insertJitAccount(Account account);
    Optional<Long> findMember(long systemId, long tenantId, long accountId);
    long insertJitMember(long id, long tenantAccessId, long systemId, long tenantId, long accountId,
                         String memberCode, String displayName, Instant now);
    void bindIdentity(long id, IdentityApi.Provider provider, IdentityApi.ExternalIdentity identity,
                      long accountId, Long memberId, Instant now);
    void touchBinding(long providerId, String externalUserId, Instant now);

    Optional<IdentityApi.MfaEnrollment> findEnrollment(long accountId, Long systemId, Long tenantId);
    IdentityApi.MfaEnrollment upsertEnrollment(long id, long accountId, Long systemId, Long tenantId,
                                                String secretRef, String secretVersion, Instant now);
    void replaceRecoveryCodes(long enrollmentId, List<RecoveryCodeHash> hashes, Instant now);
    boolean consumeRecoveryCode(long enrollmentId, String hash, Instant now);
    boolean advanceTotpStep(long enrollmentId, long expectedVersion, Long previousStep,
                            long newStep, Instant now);
    void createMfaChallenge(long id, String challengeHash, IdentityApi.Provider provider,
                            long accountId, Long systemId, Long tenantId, Instant expiresAt, Instant now);
    Optional<IdentityApi.MfaChallenge> consumeMfaChallenge(String challengeHash, Instant now);

    record RecoveryCodeHash(long id, String hash) { }
}
