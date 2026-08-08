package com.unique.examine.openapi.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiCallbackTargetPolicyTest {
    @Test
    void acceptsHttpsPublicTargetAndRejectsPrivateCredentialedAndQueryTargets() throws Exception {
        var publicAddress = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        var policy = new OpenApiCallbackTargetPolicy(host -> new InetAddress[]{publicAddress});

        assertThat(policy.requireSafe("https://callbacks.example.test/hooks/openapi").getHost())
                .isEqualTo("callbacks.example.test");
        assertThatThrownBy(() -> policy.requireSafe("http://callbacks.example.test/hook"))
                .isInstanceOf(OpenApiCallbackTargetPolicy.UnsafeCallbackTargetException.class);
        assertThatThrownBy(() -> policy.requireSafe("https://user@callbacks.example.test/hook"))
                .isInstanceOf(OpenApiCallbackTargetPolicy.UnsafeCallbackTargetException.class);
        assertThatThrownBy(() -> policy.requireSafe("https://callbacks.example.test/hook?token=x"))
                .isInstanceOf(OpenApiCallbackTargetPolicy.UnsafeCallbackTargetException.class);
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalCgnatAndIpv6UniqueLocal() throws Exception {
        for (var address : new String[]{"127.0.0.1", "10.1.2.3", "169.254.169.254",
                "100.64.0.1", "fc00::1"}) {
            var policy = new OpenApiCallbackTargetPolicy(host -> new InetAddress[]{
                    InetAddress.getByName(address)});
            assertThatThrownBy(() -> policy.requireSafe("https://callbacks.example.test/hook"))
                    .as(address)
                    .isInstanceOf(OpenApiCallbackTargetPolicy.UnsafeCallbackTargetException.class);
        }
    }
}
