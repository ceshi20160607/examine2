package com.unique.examine.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Deployment-only SMTP transport settings. Credentials are always SecretRefs. */
@ConfigurationProperties(prefix = "examine.event.delivery.smtp")
public final class BusinessSmtpProperties {
    private boolean enabled;
    private String host;
    private int port = 587;
    private String from;
    private boolean authentication = true;
    private String usernameSecretRef;
    private String passwordSecretRef;
    private boolean startTls = true;
    private boolean startTlsRequired = true;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Duration writeTimeout = Duration.ofSeconds(5);

    public boolean isReady() {
        return enabled && text(host) && validPort(port) && validEmail(from)
                && validTimeout(connectTimeout) && validTimeout(readTimeout)
                && validTimeout(writeTimeout)
                && (!startTlsRequired || startTls)
                && (!authentication
                || secretReference(usernameSecretRef) && secretReference(passwordSecretRef));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = strip(host);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = strip(from);
    }

    public boolean isAuthentication() {
        return authentication;
    }

    public void setAuthentication(boolean authentication) {
        this.authentication = authentication;
    }

    public String getUsernameSecretRef() {
        return usernameSecretRef;
    }

    public void setUsernameSecretRef(String usernameSecretRef) {
        this.usernameSecretRef = strip(usernameSecretRef);
    }

    public String getPasswordSecretRef() {
        return passwordSecretRef;
    }

    public void setPasswordSecretRef(String passwordSecretRef) {
        this.passwordSecretRef = strip(passwordSecretRef);
    }

    public boolean isStartTls() {
        return startTls;
    }

    public void setStartTls(boolean startTls) {
        this.startTls = startTls;
    }

    public boolean isStartTlsRequired() {
        return startTlsRequired;
    }

    public void setStartTlsRequired(boolean startTlsRequired) {
        this.startTlsRequired = startTlsRequired;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    private static boolean validPort(int value) {
        return value > 0 && value <= 65_535;
    }

    private static boolean validTimeout(Duration value) {
        return value != null && !value.isNegative() && !value.isZero()
                && value.compareTo(Duration.ofSeconds(30)) <= 0;
    }

    private static boolean validEmail(String value) {
        if (!text(value) || value.length() > 254 || hasLineBreak(value)) return false;
        var at = value.indexOf('@');
        return at > 0 && at == value.lastIndexOf('@') && at < value.length() - 1;
    }

    private static boolean secretReference(String value) {
        return value != null && value.length() <= 512
                && (value.matches("env://[A-Z][A-Z0-9_]{0,127}")
                || value.startsWith("file://"));
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    @Override
    public String toString() {
        return "BusinessSmtpProperties[enabled=" + enabled
                + ", host=" + host + ", port=" + port + ", from=" + from
                + ", authentication=" + authentication
                + ", usernameSecretRef=[redacted], passwordSecretRef=[redacted]"
                + ", startTls=" + startTls + ", startTlsRequired=" + startTlsRequired
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                + ", writeTimeout=" + writeTimeout + "]";
    }
}
