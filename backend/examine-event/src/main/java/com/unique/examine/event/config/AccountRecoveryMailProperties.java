package com.unique.examine.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Locale;

@ConfigurationProperties(prefix = "examine.event.account-recovery-mail")
public final class AccountRecoveryMailProperties {
    private boolean enabled;
    private String host;
    private int port = 587;
    private String username;
    private String password;
    private boolean smtpAuth;
    private boolean startTls = true;
    private String from;
    private URI publicBaseUrl;

    public boolean isReady() {
        return enabled
                && text(host)
                && port > 0 && port <= 65_535
                && validEmail(from)
                && trustedBaseUrl(publicBaseUrl)
                && (!smtpAuth || text(username) && text(password));
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = strip(username);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSmtpAuth() {
        return smtpAuth;
    }

    public void setSmtpAuth(boolean smtpAuth) {
        this.smtpAuth = smtpAuth;
    }

    public boolean isStartTls() {
        return startTls;
    }

    public void setStartTls(boolean startTls) {
        this.startTls = startTls;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = strip(from);
    }

    public URI getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(URI publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    private static boolean trustedBaseUrl(URI value) {
        if (value == null || value.getHost() == null || value.getHost().isBlank()
                || value.getUserInfo() != null || value.getQuery() != null
                || value.getFragment() != null) {
            return false;
        }
        var scheme = value.getScheme() == null
                ? "" : value.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return true;
        }
        return "http".equals(scheme) && loopback(value.getHost());
    }

    private static boolean loopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host);
    }

    private static boolean validEmail(String value) {
        if (!text(value) || value.length() > 254
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return false;
        }
        var at = value.indexOf('@');
        return at > 0 && at == value.lastIndexOf('@') && at < value.length() - 1;
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    @Override
    public String toString() {
        return "AccountRecoveryMailProperties[enabled=" + enabled
                + ", host=" + host + ", port=" + port
                + ", username=[redacted], password=[redacted], smtpAuth=" + smtpAuth
                + ", startTls=" + startTls + ", from=" + from
                + ", publicBaseUrl=" + publicBaseUrl + "]";
    }
}
