package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformSecretResolverFacadeTest {

    @Test
    void acceptsOnlyABoundedPlatformReferenceWithoutScopeIds() {
        var request = new PlatformSecretResolverFacade.SecretRequest("  env://PLATFORM_AI_KEY  ");

        assertThat(request.reference()).isEqualTo("env://PLATFORM_AI_KEY");
        assertThat(Arrays.stream(PlatformSecretResolverFacade.SecretRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("reference")
                .doesNotContain("systemId", "tenantId");
        assertThatThrownBy(() -> new PlatformSecretResolverFacade.SecretRequest(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
