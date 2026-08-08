package com.unique.examine.plat.identity;

import java.time.Instant;
import java.util.List;

interface EnterpriseIdentityClient {
    Preflight preflight(IdentityApi.Provider provider);
    IdentityApi.ExternalIdentity exchange(IdentityApi.Provider provider, String code,
                                          String codeVerifier, String nonce, Instant now);

    IdentityApi.ExternalIdentity authenticateDirectory(IdentityApi.Provider provider,
                                                       String username,
                                                       char[] password,
                                                       Instant now);

    record Preflight(boolean successful, String failureCode, List<String> checks) { }
}
