package com.unique.examine.plat.identity;

import com.unique.examine.core.api.PlatformSecretResolverFacade;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class EnterpriseLdapAdapter {
    private final PlatformSecretResolverFacade secrets;
    private final boolean allowLoopback;

    EnterpriseLdapAdapter(PlatformSecretResolverFacade secrets, boolean allowLoopback) {
        this.secrets = secrets;
        this.allowLoopback = allowLoopback;
    }

    EnterpriseIdentityClient.Preflight preflight(IdentityApi.Provider p) {
        var checks = new ArrayList<String>();
        try {
            IdentityNetworkPolicy.ldaps(p.directoryEndpoint(), allowLoopback);
            checks.add("directory-endpoint-policy");
            validateBaseDn(p.issuerUri());
            checks.add("directory-base-dn");
            try (var bindPassword = secret(p);
                 var context = context(p, p.clientId(), bindPassword.characters())) {
                context.getAttributes("", new String[]{"supportedLDAPVersion", "namingContexts"});
            }
            checks.add("directory-service-bind");
            return new EnterpriseIdentityClient.Preflight(true, null, List.copyOf(checks));
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            return new EnterpriseIdentityClient.Preflight(false, e.code, List.copyOf(checks));
        } catch (Exception e) {
            return new EnterpriseIdentityClient.Preflight(false, "LDAP_BIND_FAILED", List.copyOf(checks));
        }
    }

    IdentityApi.ExternalIdentity authenticate(IdentityApi.Provider p, String username,
                                               char[] password, Instant now) {
        if (p.protocol() != IdentityApi.Protocol.LDAP && p.protocol() != IdentityApi.Protocol.AD) {
            throw failure("IDENTITY_PROTOCOL_DIRECTORY_UNSUPPORTED");
        }
        if (username == null || username.isBlank() || username.length() > 254
                || password == null || password.length < 1 || password.length > 1024) {
            throw failure("LDAP_CREDENTIAL_INVALID");
        }
        IdentityNetworkPolicy.ldaps(p.directoryEndpoint(), allowLoopback);
        validateBaseDn(p.issuerUri());
        var mapping = mappings(p);
        try (var bindPassword = secret(p);
             var service = context(p, p.clientId(), bindPassword.characters())) {
            var result = searchOne(service, p.issuerUri(), mapping.get("externalUserId"), username,
                    new LinkedHashSet<>(mapping.values()).toArray(String[]::new));
            var userDn = result.getNameInNamespace();
            try (var ignored = context(p, userDn, password)) {
                // A successful simple bind proves the submitted end-user credential.
            }
            return identity(result.getAttributes(), mapping);
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            throw e;
        } catch (javax.naming.AuthenticationException denied) {
            throw failure("LDAP_USER_BIND_DENIED");
        } catch (Exception e) {
            throw new OidcEnterpriseIdentityClient.IdentityTransportException("LDAP_DIRECTORY_FAILED", e);
        }
    }

    private SearchResult searchOne(DirContext context, String baseDn, String attribute,
                                   String value, String[] returning) throws Exception {
        var controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setCountLimit(2);
        controls.setTimeLimit(5000);
        controls.setReturningAttributes(returning);
        var filter = "(" + attribute + "=" + escapeFilter(value) + ")";
        NamingEnumeration<SearchResult> results = null;
        try {
            results = context.search(baseDn, filter, controls);
            if (!results.hasMore()) throw failure("LDAP_USER_NOT_FOUND");
            var result = results.next();
            if (results.hasMore()) throw failure("LDAP_USER_AMBIGUOUS");
            return result;
        } finally {
            if (results != null) results.close();
        }
    }

    private IdentityApi.ExternalIdentity identity(Attributes attributes, Map<String, String> mapping)
            throws Exception {
        var claims = new LinkedHashMap<String, Object>();
        var all = attributes.getAll();
        try {
            while (all.hasMore()) {
                var attribute = all.next();
                var values = attribute.getAll();
                var collected = new ArrayList<String>();
                try {
                    while (values.hasMore()) collected.add(String.valueOf(values.next()));
                } finally { values.close(); }
                claims.put(attribute.getID(), collected.size() == 1 ? collected.getFirst() : List.copyOf(collected));
            }
        } finally { all.close(); }
        var subject = string(attributes, mapping.get("externalUserId"));
        if (subject == null || subject.isBlank()) throw failure("IDENTITY_SUBJECT_MISSING");
        return new IdentityApi.ExternalIdentity(subject, string(attributes, mapping.get("email")),
                string(attributes, mapping.get("displayName")), string(attributes, mapping.get("mobile")),
                string(attributes, mapping.get("employeeNo")), string(attributes, mapping.get("departmentId")),
                Map.copyOf(claims));
    }

    private CloseableDirContext context(IdentityApi.Provider p, String principal, char[] credential) throws Exception {
        var environment = new Hashtable<String, Object>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, p.directoryEndpoint());
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        environment.put(Context.SECURITY_PRINCIPAL, principal);
        environment.put(Context.SECURITY_CREDENTIALS, new String(credential));
        environment.put(Context.REFERRAL, "throw");
        environment.put("com.sun.jndi.ldap.connect.timeout", "5000");
        environment.put("com.sun.jndi.ldap.read.timeout", "5000");
        return new CloseableDirContext(new InitialDirContext(environment));
    }

    private SecretCharacters secret(IdentityApi.Provider p) {
        if (p.secretRef() == null || p.secretVersion() == null) throw failure("IDENTITY_SECRET_REF_MISSING");
        byte[] bytes = null;
        try (var value = secrets.resolve(new PlatformSecretResolverFacade.SecretRequest(p.secretRef()))
                .orElseThrow(() -> failure("IDENTITY_SECRET_UNRESOLVED"))) {
            bytes = value.copyBytes();
            return new SecretCharacters(new String(bytes, StandardCharsets.UTF_8).toCharArray());
        } finally {
            if (bytes != null) java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private static Map<String, String> mappings(IdentityApi.Provider p) {
        var values = new LinkedHashMap<String, String>();
        boolean ad = p.protocol() == IdentityApi.Protocol.AD;
        values.put("externalUserId", ad ? "sAMAccountName" : "uid");
        values.put("email", "mail"); values.put("displayName", "displayName");
        values.put("mobile", "mobile"); values.put("employeeNo", ad ? "employeeID" : "employeeNumber");
        values.put("departmentId", ad ? "department" : "ou");
        values.putAll(p.attributeMapping());
        values.replaceAll((key, value) -> attributeName(value));
        return Map.copyOf(values);
    }

    private static String attributeName(String value) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9;-]{0,63}$")) {
            throw failure("LDAP_ATTRIBUTE_MAPPING_INVALID");
        }
        return value;
    }

    static String escapeFilter(String value) {
        var escaped = new StringBuilder(value.length());
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = current & 0xff;
            if (unsigned == 0 || unsigned == '(' || unsigned == ')' || unsigned == '*' || unsigned == '\\'
                    || unsigned < 0x20 || unsigned >= 0x7f) {
                escaped.append('\\').append("%02x".formatted(unsigned));
            } else escaped.append((char) unsigned);
        }
        return escaped.toString();
    }

    private static void validateBaseDn(String value) {
        try {
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            new javax.naming.ldap.LdapName(value);
        } catch (Exception e) { throw failure("LDAP_BASE_DN_INVALID"); }
    }
    private static String string(Attributes attributes, String name) throws Exception {
        var value = attributes.get(name); return value == null || value.size() == 0 ? null : String.valueOf(value.get());
    }
    private static OidcEnterpriseIdentityClient.IdentityTransportException failure(String code) {
        return new OidcEnterpriseIdentityClient.IdentityTransportException(code);
    }

    private static final class SecretCharacters implements AutoCloseable {
        private final char[] value;
        SecretCharacters(char[] value) { this.value = value; }
        char[] characters() { return value; }
        @Override public void close() { java.util.Arrays.fill(value, '\0'); }
    }

    private static final class CloseableDirContext implements DirContext, AutoCloseable {
        private final DirContext delegate;
        CloseableDirContext(DirContext delegate) { this.delegate = delegate; }
        @Override public void close() throws javax.naming.NamingException { delegate.close(); }
        @Override public Attributes getAttributes(String name) throws javax.naming.NamingException { return delegate.getAttributes(name); }
        @Override public Attributes getAttributes(String name, String[] attrIds) throws javax.naming.NamingException { return delegate.getAttributes(name, attrIds); }
        @Override public Attributes getAttributes(javax.naming.Name name) throws javax.naming.NamingException { return delegate.getAttributes(name); }
        @Override public Attributes getAttributes(javax.naming.Name name, String[] attrIds) throws javax.naming.NamingException { return delegate.getAttributes(name, attrIds); }
        @Override public void modifyAttributes(String name, int modOp, Attributes attrs) throws javax.naming.NamingException { delegate.modifyAttributes(name, modOp, attrs); }
        @Override public void modifyAttributes(javax.naming.Name name, int modOp, Attributes attrs) throws javax.naming.NamingException { delegate.modifyAttributes(name, modOp, attrs); }
        @Override public void modifyAttributes(String name, javax.naming.directory.ModificationItem[] mods) throws javax.naming.NamingException { delegate.modifyAttributes(name, mods); }
        @Override public void modifyAttributes(javax.naming.Name name, javax.naming.directory.ModificationItem[] mods) throws javax.naming.NamingException { delegate.modifyAttributes(name, mods); }
        @Override public void bind(String name, Object obj, Attributes attrs) throws javax.naming.NamingException { delegate.bind(name, obj, attrs); }
        @Override public void bind(javax.naming.Name name, Object obj, Attributes attrs) throws javax.naming.NamingException { delegate.bind(name, obj, attrs); }
        @Override public void rebind(String name, Object obj, Attributes attrs) throws javax.naming.NamingException { delegate.rebind(name, obj, attrs); }
        @Override public void rebind(javax.naming.Name name, Object obj, Attributes attrs) throws javax.naming.NamingException { delegate.rebind(name, obj, attrs); }
        @Override public DirContext createSubcontext(String name, Attributes attrs) throws javax.naming.NamingException { return delegate.createSubcontext(name, attrs); }
        @Override public DirContext createSubcontext(javax.naming.Name name, Attributes attrs) throws javax.naming.NamingException { return delegate.createSubcontext(name, attrs); }
        @Override public DirContext getSchema(String name) throws javax.naming.NamingException { return delegate.getSchema(name); }
        @Override public DirContext getSchema(javax.naming.Name name) throws javax.naming.NamingException { return delegate.getSchema(name); }
        @Override public DirContext getSchemaClassDefinition(String name) throws javax.naming.NamingException { return delegate.getSchemaClassDefinition(name); }
        @Override public DirContext getSchemaClassDefinition(javax.naming.Name name) throws javax.naming.NamingException { return delegate.getSchemaClassDefinition(name); }
        @Override public NamingEnumeration<SearchResult> search(String name, Attributes matchingAttributes, String[] attributesToReturn) throws javax.naming.NamingException { return delegate.search(name, matchingAttributes, attributesToReturn); }
        @Override public NamingEnumeration<SearchResult> search(javax.naming.Name name, Attributes matchingAttributes, String[] attributesToReturn) throws javax.naming.NamingException { return delegate.search(name, matchingAttributes, attributesToReturn); }
        @Override public NamingEnumeration<SearchResult> search(String name, Attributes matchingAttributes) throws javax.naming.NamingException { return delegate.search(name, matchingAttributes); }
        @Override public NamingEnumeration<SearchResult> search(javax.naming.Name name, Attributes matchingAttributes) throws javax.naming.NamingException { return delegate.search(name, matchingAttributes); }
        @Override public NamingEnumeration<SearchResult> search(String name, String filter, SearchControls cons) throws javax.naming.NamingException { return delegate.search(name, filter, cons); }
        @Override public NamingEnumeration<SearchResult> search(javax.naming.Name name, String filter, SearchControls cons) throws javax.naming.NamingException { return delegate.search(name, filter, cons); }
        @Override public NamingEnumeration<SearchResult> search(String name, String filterExpr, Object[] filterArgs, SearchControls cons) throws javax.naming.NamingException { return delegate.search(name, filterExpr, filterArgs, cons); }
        @Override public NamingEnumeration<SearchResult> search(javax.naming.Name name, String filterExpr, Object[] filterArgs, SearchControls cons) throws javax.naming.NamingException { return delegate.search(name, filterExpr, filterArgs, cons); }
        @Override public Object lookup(javax.naming.Name name) throws javax.naming.NamingException { return delegate.lookup(name); }
        @Override public Object lookup(String name) throws javax.naming.NamingException { return delegate.lookup(name); }
        @Override public void bind(javax.naming.Name name, Object obj) throws javax.naming.NamingException { delegate.bind(name, obj); }
        @Override public void bind(String name, Object obj) throws javax.naming.NamingException { delegate.bind(name, obj); }
        @Override public void rebind(javax.naming.Name name, Object obj) throws javax.naming.NamingException { delegate.rebind(name, obj); }
        @Override public void rebind(String name, Object obj) throws javax.naming.NamingException { delegate.rebind(name, obj); }
        @Override public void unbind(javax.naming.Name name) throws javax.naming.NamingException { delegate.unbind(name); }
        @Override public void unbind(String name) throws javax.naming.NamingException { delegate.unbind(name); }
        @Override public void rename(javax.naming.Name oldName, javax.naming.Name newName) throws javax.naming.NamingException { delegate.rename(oldName, newName); }
        @Override public void rename(String oldName, String newName) throws javax.naming.NamingException { delegate.rename(oldName, newName); }
        @Override public NamingEnumeration<javax.naming.NameClassPair> list(javax.naming.Name name) throws javax.naming.NamingException { return delegate.list(name); }
        @Override public NamingEnumeration<javax.naming.NameClassPair> list(String name) throws javax.naming.NamingException { return delegate.list(name); }
        @Override public NamingEnumeration<javax.naming.Binding> listBindings(javax.naming.Name name) throws javax.naming.NamingException { return delegate.listBindings(name); }
        @Override public NamingEnumeration<javax.naming.Binding> listBindings(String name) throws javax.naming.NamingException { return delegate.listBindings(name); }
        @Override public void destroySubcontext(javax.naming.Name name) throws javax.naming.NamingException { delegate.destroySubcontext(name); }
        @Override public void destroySubcontext(String name) throws javax.naming.NamingException { delegate.destroySubcontext(name); }
        @Override public Context createSubcontext(javax.naming.Name name) throws javax.naming.NamingException { return delegate.createSubcontext(name); }
        @Override public Context createSubcontext(String name) throws javax.naming.NamingException { return delegate.createSubcontext(name); }
        @Override public Object lookupLink(javax.naming.Name name) throws javax.naming.NamingException { return delegate.lookupLink(name); }
        @Override public Object lookupLink(String name) throws javax.naming.NamingException { return delegate.lookupLink(name); }
        @Override public javax.naming.NameParser getNameParser(javax.naming.Name name) throws javax.naming.NamingException { return delegate.getNameParser(name); }
        @Override public javax.naming.NameParser getNameParser(String name) throws javax.naming.NamingException { return delegate.getNameParser(name); }
        @Override public javax.naming.Name composeName(javax.naming.Name name, javax.naming.Name prefix) throws javax.naming.NamingException { return delegate.composeName(name, prefix); }
        @Override public String composeName(String name, String prefix) throws javax.naming.NamingException { return delegate.composeName(name, prefix); }
        @Override public Object addToEnvironment(String propName, Object propVal) throws javax.naming.NamingException { return delegate.addToEnvironment(propName, propVal); }
        @Override public Object removeFromEnvironment(String propName) throws javax.naming.NamingException { return delegate.removeFromEnvironment(propName); }
        @Override public Hashtable<?, ?> getEnvironment() throws javax.naming.NamingException { return delegate.getEnvironment(); }
        @Override public String getNameInNamespace() throws javax.naming.NamingException { return delegate.getNameInNamespace(); }
    }
}
