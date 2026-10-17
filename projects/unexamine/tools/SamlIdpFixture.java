import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * 仅用于隔离发布包验收的本地 SAML2 身份源。临时证书和私钥只留在忽略的测试安装目录，
 * 浏览器经过真实 Redirect AuthnRequest、身份源授权页和签名 POST Response。
 */
public class SamlIdpFixture {
    private static final int PORT = 18213;
    private static final String IDP = "http://127.0.0.1:18213/issuer";
    private static final String ENDPOINT = "http://127.0.0.1:18213/sso";
    private static final String SP = "s19-saml-local-sp";
    private static final String ACS = System.getenv().getOrDefault("S19_SAML_ACS",
            "http://127.0.0.1:15185/api/auth/sso/s19-saml2/saml/acs");
    private static final String EMAIL = System.getenv().getOrDefault("S19_SAML_EMAIL", "member@example.test");
    private static final String PASSWORD = "saml-test-only";
    private final PrivateKey key;
    private final Map<String, String> requests = new HashMap<>();

    private SamlIdpFixture(PrivateKey key) {
        this.key = key;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: java SamlIdpFixture.java <ignored-fixture-dir>");
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        Path keystore = directory.resolve("saml-idp.p12");
        if (!Files.exists(keystore)) {
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048",
                    "-sigalg", "SHA256withRSA", "-storetype", "PKCS12", "-keystore", keystore.toString(),
                    "-storepass", PASSWORD, "-keypass", PASSWORD, "-dname", "CN=S19 Local SAML IdP",
                    "-validity", "365", "-noprompt").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) throw new IllegalStateException("Fixture certificate generation failed: " + output);
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keystore)) { store.load(input, PASSWORD.toCharArray()); }
        X509Certificate certificate = (X509Certificate) store.getCertificate("fixture");
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
        Files.writeString(directory.resolve("saml-idp.pem"), pem, StandardCharsets.US_ASCII);
        SamlIdpFixture fixture = new SamlIdpFixture((PrivateKey) store.getKey("fixture", PASSWORD.toCharArray()));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/health", exchange -> fixture.respond(exchange, 200, "ok", "text/plain"));
        server.createContext("/sso", fixture::authorize);
        server.createContext("/respond", fixture::signedResponse);
        server.start();
        System.out.println("S19_SAML_IDP_READY " + ENDPOINT + " " + directory.resolve("saml-idp.pem"));
    }

    private void authorize(HttpExchange exchange) throws java.io.IOException {
        try {
            Map<String, String> query = form(exchange.getRequestURI().getRawQuery());
            String relay = query.get("RelayState");
            String request = inflate(query.get("SAMLRequest"));
            Document document = parse(request);
            Element root = document.getDocumentElement();
            if (!"AuthnRequest".equals(root.getLocalName())
                    || !ENDPOINT.equals(root.getAttribute("Destination"))
                    || !ACS.equals(root.getAttribute("AssertionConsumerServiceURL"))
                    || !SP.equals(root.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Issuer")
                            .item(0).getTextContent()) || relay == null || relay.isBlank()) {
                respond(exchange, 400, "Invalid SAML request", "text/plain");
                return;
            }
            requests.put(relay, root.getAttribute("ID"));
            String page = "<!doctype html><html lang=\"zh\"><meta charset=\"utf-8\"><title>隔离 SAML2 身份源</title>"
                    + "<body><h1>隔离 SAML2 身份源</h1><p>测试成员 " + EMAIL + "</p>"
                    + "<form action=\"/respond?RelayState=" + relay + "\" method=\"post\">"
                    + "<button type=\"submit\">授权并返回</button></form></body></html>";
            respond(exchange, 200, page, "text/html; charset=utf-8");
        } catch (Exception exception) {
            respond(exchange, 400, "Invalid SAML request", "text/plain");
        }
    }

    private void signedResponse(HttpExchange exchange) throws java.io.IOException {
        String relay = form(exchange.getRequestURI().getRawQuery()).get("RelayState");
        String requestId = requests.remove(relay);
        if (requestId == null) {
            respond(exchange, 400, "SAML request expired or already used", "text/plain");
            return;
        }
        try {
            String response = Base64.getEncoder().encodeToString(sign(requestId));
            String page = "<!doctype html><html lang=\"zh\"><meta charset=\"utf-8\">"
                    + "<title>返回业务平台</title><body><form id=\"saml\" action=\"" + ACS + "\" method=\"post\">"
                    + "<input type=\"hidden\" name=\"SAMLResponse\" value=\"" + response + "\">"
                    + "<input type=\"hidden\" name=\"RelayState\" value=\"" + relay + "\">"
                    + "<button type=\"submit\">继续登录</button></form><script>document.getElementById('saml').submit()</script>"
                    + "</body></html>";
            respond(exchange, 200, page, "text/html; charset=utf-8");
        } catch (Exception exception) {
            respond(exchange, 500, "SAML fixture signing failed", "text/plain");
        }
    }

    private byte[] sign(String requestId) throws Exception {
        Instant now = Instant.now();
        String responseId = "_" + UUID.randomUUID();
        String assertionId = "_" + UUID.randomUUID();
        String xml = "<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                + "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ID=\"" + responseId + "\" "
                + "Version=\"2.0\" IssueInstant=\"" + now + "\" Destination=\"" + ACS
                + "\" InResponseTo=\"" + requestId + "\">"
                + "<saml:Issuer>" + IDP + "</saml:Issuer>"
                + "<samlp:Status><samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/></samlp:Status>"
                + "<saml:Assertion ID=\"" + assertionId + "\" IssueInstant=\"" + now + "\" Version=\"2.0\">"
                + "<saml:Issuer>" + IDP + "</saml:Issuer><saml:Subject><saml:NameID>s19-saml-subject</saml:NameID>"
                + "<saml:SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
                + "<saml:SubjectConfirmationData Recipient=\"" + ACS + "\" InResponseTo=\"" + requestId
                + "\" NotOnOrAfter=\"" + now.plusSeconds(300) + "\"/></saml:SubjectConfirmation></saml:Subject>"
                + "<saml:Conditions NotBefore=\"" + now.minusSeconds(60) + "\" NotOnOrAfter=\""
                + now.plusSeconds(300) + "\"><saml:AudienceRestriction><saml:Audience>" + SP
                + "</saml:Audience></saml:AudienceRestriction></saml:Conditions>"
                + "<saml:AuthnStatement AuthnInstant=\"" + now + "\"/>"
                + "<saml:AttributeStatement><saml:Attribute Name=\"email\"><saml:AttributeValue>" + EMAIL
                + "</saml:AttributeValue></saml:Attribute><saml:Attribute Name=\"email_verified\">"
                + "<saml:AttributeValue>true</saml:AttributeValue></saml:Attribute></saml:AttributeStatement>"
                + "</saml:Assertion></samlp:Response>";
        Document document = parse(xml);
        Element root = document.getDocumentElement();
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        Reference reference = factory.newReference("#" + responseId,
                factory.newDigestMethod(DigestMethod.SHA256, null),
                java.util.List.of(factory.newTransform(Transform.ENVELOPED,
                        (javax.xml.crypto.dsig.spec.TransformParameterSpec) null)), null, null);
        SignedInfo info = factory.newSignedInfo(factory.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,
                (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null), java.util.List.of(reference));
        DOMSignContext context = new DOMSignContext(key, root,
                root.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:protocol", "Status").item(0));
        context.setIdAttributeNS(root, null, "ID");
        factory.newXMLSignature(info, null).sign(context);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(bytes));
        return bytes.toByteArray();
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String inflate(String encoded) throws Exception {
        byte[] compressed = Base64.getDecoder().decode(encoded);
        try (var input = new InflaterInputStream(new ByteArrayInputStream(compressed), new Inflater(true))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> form(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null) return values;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    private void respond(HttpExchange exchange, int status, String body, String type) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) { stream.write(bytes); }
    }
}
