package com.unique.examine.plat.identity;

import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.naming.Context;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.InitialDirContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class EnterpriseLdapAdapterIntegrationTest {
    private static final String BASE_DN = "dc=example,dc=org";
    private static final String ADMIN_DN = "cn=admin," + BASE_DN;
    private static final String ADMIN_PASSWORD = "directory-admin-test-secret";
    private static final String USER_DN = "uid=person42," + BASE_DN;

    @Container
    static final GenericContainer<?> LDAP = new GenericContainer<>(
            DockerImageName.parse("osixia/openldap:1.5.0"))
            .withEnv("LDAP_ORGANISATION", "Enterprise Identity Test")
            .withEnv("LDAP_DOMAIN", "example.org")
            .withEnv("LDAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withEnv("LDAP_TLS", "false")
            .withExposedPorts(389)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    @BeforeAll
    static void seedDirectory() throws Exception {
        var attributes = new BasicAttributes(true);
        var objectClass = new BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("person");
        objectClass.add("organizationalPerson");
        objectClass.add("inetOrgPerson");
        attributes.put(objectClass);
        attributes.put("uid", "person42");
        attributes.put("cn", "Identity Person");
        attributes.put("sn", "Person");
        attributes.put("displayName", "Identity Person");
        attributes.put("mail", "person42@example.org");
        attributes.put("employeeNumber", "E-0042");
        attributes.put("userPassword", "person-password");
        var context = adminContext();
        try {
            context.createSubcontext(USER_DN, attributes).close();
        } finally {
            context.close();
        }
    }

    @Test
    void performsRealServiceBindUniqueSearchEndUserBindAndAttributeMapping() {
        var adapter = adapter(true);
        var provider = provider("ldap://127.0.0.1:" + LDAP.getMappedPort(389));

        assertThat(adapter.preflight(provider).successful()).isTrue();
        var submitted = "person-password".toCharArray();
        var identity = adapter.authenticate(provider, "person42", submitted,
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThat(identity.subject()).isEqualTo("person42");
        assertThat(identity.email()).isEqualTo("person42@example.org");
        assertThat(identity.displayName()).isEqualTo("Identity Person");
        assertThat(identity.employeeNo()).isEqualTo("E-0042");
        assertThat(identity.claims()).containsEntry("uid", "person42");
    }

    @Test
    void rejectsWrongPasswordAndEscapesFilterInjection() {
        var adapter = adapter(true);
        var provider = provider("ldap://127.0.0.1:" + LDAP.getMappedPort(389));

        assertThatThrownBy(() -> adapter.authenticate(provider, "person42",
                "wrong-password".toCharArray(), Instant.now()))
                .isInstanceOf(OidcEnterpriseIdentityClient.IdentityTransportException.class)
                .hasMessage("LDAP_USER_BIND_DENIED");
        assertThatThrownBy(() -> adapter.authenticate(provider, "person42)(uid=*",
                "person-password".toCharArray(), Instant.now()))
                .isInstanceOf(OidcEnterpriseIdentityClient.IdentityTransportException.class)
                .hasMessage("LDAP_USER_NOT_FOUND");
        assertThat(EnterpriseLdapAdapter.escapeFilter("person42)(uid=*\\"))
                .isEqualTo("person42\\29\\28uid=\\2a\\5c");
    }

    @Test
    void requiresEncryptedDirectoryTransportOutsideExplicitLoopbackTestMode() {
        var result = adapter(false).preflight(provider("ldap://directory.example.org:389"));

        assertThat(result.successful()).isFalse();
        assertThat(result.failureCode()).isEqualTo("IDENTITY_DIRECTORY_TLS_REQUIRED");
    }

    private static EnterpriseLdapAdapter adapter(boolean allowLoopback) {
        PlatformSecretResolverFacade secrets = request -> Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8(ADMIN_PASSWORD));
        return new EnterpriseLdapAdapter(secrets, allowLoopback);
    }

    private static IdentityApi.Provider provider(String endpoint) {
        return new IdentityApi.Provider(42, "enterprise-ldap", "Enterprise LDAP", IdentityApi.Protocol.LDAP,
                BASE_DN, null, null, null, endpoint, ADMIN_DN,
                "env://LDAP_ADMIN_PASSWORD", "v1", "https://app.example.org/api/v1/auth/sso/callback", "",
                List.of("example.org"), Map.of("externalUserId", "uid", "email", "mail",
                "displayName", "displayName", "employeeNo", "employeeNumber"),
                true, false, null, null, IdentityApi.MfaPolicy.REQUIRED, IdentityApi.Status.PUBLISHED,
                "PASSED", 0L, null, null, null, 0);
    }

    private static InitialDirContext adminContext() throws Exception {
        var environment = new Hashtable<String, Object>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://127.0.0.1:" + LDAP.getMappedPort(389));
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        environment.put(Context.SECURITY_PRINCIPAL, ADMIN_DN);
        environment.put(Context.SECURITY_CREDENTIALS, ADMIN_PASSWORD);
        environment.put("com.sun.jndi.ldap.connect.timeout", "5000");
        environment.put("com.sun.jndi.ldap.read.timeout", "5000");
        return new InitialDirContext(environment);
    }
}
