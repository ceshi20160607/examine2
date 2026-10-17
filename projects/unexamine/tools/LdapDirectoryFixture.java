import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;

/** S19.5 隔离验收目录：只在本机提供受证书保护的 LDAPS 和固定测试用户。 */
public final class LdapDirectoryFixture {
    private LdapDirectoryFixture() {}

    /** 启动后保持服务，供真实后台预检及浏览器凭据登录共同使用。 */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: LdapDirectoryFixture <pkcs12-file> <store-password> <port>");
        }
        SSLUtil tls = new SSLUtil(new KeyStoreKeyManager(args[0], args[1].toCharArray()), null);
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=test");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPSConfig("LDAPS",
                InetAddress.getByName("127.0.0.1"), Integer.parseInt(args[2]),
                tls.createSSLServerSocketFactory(), null));
        config.addAdditionalBindCredentials("cn=Directory Manager", "fixture-service-password");
        InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
        server.add(entry("dc=example,dc=test", "domain", "dc", "example"));
        server.add(entry("ou=people,dc=example,dc=test", "organizationalUnit", "ou", "people"));
        addUser(server, "alice", "member@example.test", "fixture-user-password");
        addUser(server, "outside", "outside@other.test", "fixture-user-password");
        addUser(server, "unmapped", "unmapped@example.test", "fixture-user-password");
        addUser(server, "duplicate-a", "duplicate-a@example.test", "fixture-user-password", "duplicate");
        addUser(server, "duplicate-b", "duplicate-b@example.test", "fixture-user-password", "duplicate");
        server.startListening();
        System.out.println("LDAPS fixture ready on 127.0.0.1:" + server.getListenPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.shutDown(true)));
        new CountDownLatch(1).await();
    }

    /** 固定条目使正常、域名拒绝、缺映射及同登录名多匹配可在同一目录复验。 */
    private static void addUser(InMemoryDirectoryServer server, String cn, String mail,
            String password) throws Exception {
        addUser(server, cn, mail, password, cn);
    }

    private static void addUser(InMemoryDirectoryServer server, String cn, String mail,
            String password, String uid) throws Exception {
        Entry person = entry("cn=" + cn + ",ou=people,dc=example,dc=test", "inetOrgPerson", "cn", cn);
        person.addAttribute("sn", cn);
        person.addAttribute("uid", uid);
        person.addAttribute("mail", mail);
        person.addAttribute("userPassword", password);
        server.add(person);
    }

    private static Entry entry(String dn, String objectClass, String name, String value) {
        Entry entry = new Entry(dn);
        entry.addAttribute("objectClass", "top", objectClass);
        entry.addAttribute(name, value);
        return entry;
    }
}
