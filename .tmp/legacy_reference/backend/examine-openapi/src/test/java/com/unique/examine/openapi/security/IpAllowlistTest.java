package com.unique.examine.openapi.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpAllowlistTest {
    @Test
    void supportsExactAddressesAndIpv4AndIpv6Cidr() {
        assertThat(IpAllowlist.allows(List.of("127.0.0.1"), "127.0.0.1")).isTrue();
        assertThat(IpAllowlist.allows(List.of("10.20.0.0/16"), "10.20.8.9")).isTrue();
        assertThat(IpAllowlist.allows(List.of("2001:db8::/32"), "2001:db8::42")).isTrue();
        assertThat(IpAllowlist.allows(List.of("10.20.0.0/16"), "10.21.8.9")).isFalse();
    }

    @Test
    void emptyInvalidAndHostNameRulesFailClosed() {
        assertThat(IpAllowlist.allows(List.of(), "127.0.0.1")).isFalse();
        assertThat(IpAllowlist.allows(List.of("not-an-address"), "127.0.0.1")).isFalse();
        assertThat(IpAllowlist.allows(List.of("127.0.0.1"), "localhost")).isFalse();
        assertThatThrownBy(() -> IpAllowlist.validate(List.of("localhost")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
