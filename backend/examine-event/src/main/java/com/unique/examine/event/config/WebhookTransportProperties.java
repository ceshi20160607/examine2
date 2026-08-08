package com.unique.examine.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Fixed network safety policy for outbound event webhooks. */
@ConfigurationProperties(prefix = "examine.event.delivery.webhook")
public final class WebhookTransportProperties {
    private boolean enabled;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private boolean allowLoopbackHttpForTesting;

    public boolean isReady() {
        return enabled && validTimeout(connectTimeout) && validTimeout(requestTimeout);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isAllowLoopbackHttpForTesting() {
        return allowLoopbackHttpForTesting;
    }

    public void setAllowLoopbackHttpForTesting(boolean allowLoopbackHttpForTesting) {
        this.allowLoopbackHttpForTesting = allowLoopbackHttpForTesting;
    }

    private static boolean validTimeout(Duration value) {
        return value != null && !value.isNegative() && !value.isZero()
                && value.compareTo(Duration.ofSeconds(30)) <= 0;
    }

    @Override
    public String toString() {
        return "WebhookTransportProperties[enabled=" + enabled
                + ", connectTimeout=" + connectTimeout
                + ", requestTimeout=" + requestTimeout
                + ", allowLoopbackHttpForTesting=" + allowLoopbackHttpForTesting + "]";
    }
}
