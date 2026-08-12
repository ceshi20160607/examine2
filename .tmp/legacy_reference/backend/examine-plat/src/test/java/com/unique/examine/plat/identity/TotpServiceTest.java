package com.unique.examine.plat.identity;

import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    @Test
    void verifiesRfc6238VectorAndRejectsWrongOrMalformedCodes() {
        PlatformSecretResolverFacade secrets = request -> Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"));
        var service = new TotpService(secrets);

        assertThat(service.verify("env://MFA_TEST", "287082", Instant.ofEpochSecond(59))).isEqualTo(1);
        assertThat(service.verify("env://MFA_TEST", "287083", Instant.ofEpochSecond(59))).isEqualTo(-1);
        assertThat(service.verify("env://MFA_TEST", "123", Instant.ofEpochSecond(59))).isEqualTo(-1);
    }

    @Test
    void failsClosedWhenSecretReferenceCannotResolve() {
        var service = new TotpService(request -> Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.verify("env://MISSING", "287082", Instant.ofEpochSecond(59)))
                .isInstanceOf(TotpService.MfaException.class)
                .hasMessage("MFA_SECRET_UNRESOLVED");
    }
}

