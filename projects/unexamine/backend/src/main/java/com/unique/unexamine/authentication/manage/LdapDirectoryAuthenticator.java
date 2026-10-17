package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import java.net.URI;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** 目录身份只经受信任的 LDAPS 服务检索和用户 Bind 验证，不保存用户密码。 */
@Component
public class LdapDirectoryAuthenticator {
    private static final Pattern ATTRIBUTE = Pattern.compile("[a-zA-Z][a-zA-Z0-9-]{0,63}");
    private final SecretReferenceResolver secrets;

    public LdapDirectoryAuthenticator(SecretReferenceResolver secrets) {
        this.secrets = secrets;
    }

    /** 发布前实际完成 TLS 握手、服务账号 Bind 和搜索基准读取。 */
    public void preflight(Map<String, Object> config, String subjectAttribute, String emailAttribute) {
        DirectorySettings settings = settings(config, subjectAttribute, emailAttribute);
        try (DirectoryConnection context = connect(settings.endpoint(), settings.bindDn(),
                secrets.resolve(settings.bindSecretRef()))) {
            context.context().getAttributes(settings.searchBase());
        } catch (NamingException exception) {
            throw unavailable(exception);
        }
    }

    /** 用户名只作为 LDAP 过滤器参数；只接受唯一目录条目并用该条目 DN 重新 Bind。 */
    public Map<String, Object> authenticate(Map<String, Object> config, String subjectAttribute,
            String emailAttribute, String username, String password) {
        DirectorySettings settings = settings(config, subjectAttribute, emailAttribute);
        if (username == null || username.isBlank() || username.length() > 128
                || password == null || password.isBlank()) {
            throw new DomainException("SSO_DIRECTORY_CREDENTIAL_INVALID", "目录账号或密码无效", HttpStatus.UNAUTHORIZED);
        }
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setCountLimit(2);
        controls.setTimeLimit(5000);
        controls.setReturningAttributes(new String[]{settings.subjectAttribute(), settings.emailAttribute()});
        try (DirectoryConnection service = connect(settings.endpoint(), settings.bindDn(),
                secrets.resolve(settings.bindSecretRef()))) {
            String filter = "(" + settings.loginAttribute() + "={0})";
            NamingEnumeration<SearchResult> results = service.context().search(settings.searchBase(), filter,
                    new Object[]{username}, controls);
            try {
                if (!results.hasMore()) {
                    throw new DomainException("SSO_DIRECTORY_IDENTITY_MISSING", "目录账号不存在", HttpStatus.UNAUTHORIZED);
                }
                SearchResult result = results.next();
                if (results.hasMore()) {
                    throw new DomainException("SSO_DIRECTORY_IDENTITY_CONFLICT", "目录账号匹配多个用户", HttpStatus.CONFLICT);
                }
                String subject = singleValue(result.getAttributes(), settings.subjectAttribute());
                String email = singleValue(result.getAttributes(), settings.emailAttribute());
                if (subject == null) {
                    throw new DomainException("SSO_SUBJECT_MISSING", "目录用户缺少外部用户标识", HttpStatus.UNAUTHORIZED);
                }
                try (DirectoryConnection ignored = connect(settings.endpoint(), result.getNameInNamespace(), password)) {
                    Map<String, Object> identity = new LinkedHashMap<>();
                    identity.put(settings.subjectAttribute(), subject);
                    if (email != null) {
                        identity.put(settings.emailAttribute(), email);
                        // 邮箱来自受信任目录中的已认证条目，不取用户提交的邮箱文本。
                        identity.put("email_verified", true);
                    }
                    return identity;
                }
            } finally {
                results.close();
            }
        } catch (AuthenticationException exception) {
            throw new DomainException("SSO_DIRECTORY_CREDENTIAL_INVALID", "目录账号或密码无效", HttpStatus.UNAUTHORIZED);
        } catch (NamingException exception) {
            throw unavailable(exception);
        }
    }

    private DirectorySettings settings(Map<String, Object> config, String subjectAttribute, String emailAttribute) {
        String endpoint = value(config.get("directoryEndpoint"));
        String bindDn = value(config.get("bindDn"));
        String secretRef = value(config.get("bindSecretRef"));
        String searchBase = value(config.get("searchBase"));
        String loginAttribute = value(config.get("loginAttribute"));
        try {
            URI uri = URI.create(endpoint == null ? "" : endpoint);
            if (!"ldaps".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getPort() == 0
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !"".equals(uri.getRawPath())) {
                throw new IllegalArgumentException("unsafe directory endpoint");
            }
            // 空 DN 可能让目录把服务账号降级为匿名 Bind；空搜索基准会扩大检索范围。
            if (bindDn == null || searchBase == null) {
                throw new IllegalArgumentException("missing bind or search scope");
            }
            new LdapName(bindDn);
            new LdapName(searchBase);
            if (secretRef == null || loginAttribute == null || subjectAttribute == null || emailAttribute == null
                    || !ATTRIBUTE.matcher(loginAttribute).matches()
                    || !ATTRIBUTE.matcher(subjectAttribute).matches()
                    || !ATTRIBUTE.matcher(emailAttribute).matches()) {
                throw new IllegalArgumentException("invalid directory mapping");
            }
        } catch (IllegalArgumentException | NamingException exception) {
            throw new DomainException("SSO_DIRECTORY_CONFIG_INVALID", "目录端点、Bind DN、搜索基准或属性配置无效", HttpStatus.CONFLICT);
        }
        return new DirectorySettings(endpoint, bindDn, secretRef, searchBase,
                loginAttribute, subjectAttribute, emailAttribute);
    }

    private DirectoryConnection connect(String endpoint, String dn, String password) throws NamingException {
        Hashtable<String, Object> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, endpoint);
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        environment.put(Context.SECURITY_PRINCIPAL, dn);
        environment.put(Context.SECURITY_CREDENTIALS, password);
        environment.put(Context.REFERRAL, "throw");
        environment.put("com.sun.jndi.ldap.connect.timeout", "5000");
        environment.put("com.sun.jndi.ldap.read.timeout", "5000");
        return new DirectoryConnection(new InitialLdapContext(environment, null));
    }

    private String singleValue(Attributes attributes, String name) throws NamingException {
        if (attributes.get(name) == null || attributes.get(name).size() != 1) return null;
        Object value = attributes.get(name).get();
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private String value(Object input) {
        return input == null || input.toString().isBlank() ? null : input.toString().strip();
    }

    private DomainException unavailable(NamingException exception) {
        return new DomainException("SSO_DIRECTORY_UNAVAILABLE", "受信任目录连接或检索失败", HttpStatus.BAD_GATEWAY);
    }

    private record DirectorySettings(String endpoint, String bindDn, String bindSecretRef,
            String searchBase, String loginAttribute, String subjectAttribute, String emailAttribute) {}

    private record DirectoryConnection(LdapContext context) implements AutoCloseable {
        @Override public void close() throws NamingException { context.close(); }
    }
}
