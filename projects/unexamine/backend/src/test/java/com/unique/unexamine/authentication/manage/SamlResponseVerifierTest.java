package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用 JDK 自带 keytool 建立临时测试证书，验证真实 XML 签名与断言边界。 */
class SamlResponseVerifierTest {
    private static final String ACS = "https://service.example.test/api/auth/sso/saml-test/saml/acs";
    private static final String IDP = "https://idp.example.test/saml";
    private static final String SP = "https://service.example.test/saml";
    private static final String REQUEST = "_request-1";
    private final SamlResponseVerifier verifier = new SamlResponseVerifier();
    private final Clock clock = Clock.systemUTC();

    @TempDir Path temporary;

    @Test
    void acceptsOnlySignedResponseBoundToRequestAndService() throws Exception {
        KeyMaterial key = material(temporary);
        String signed = signed(key, REQUEST, SP, clock.instant().plusSeconds(300), ACS, IDP, clock.instant());
        assertThat(verifier.validCertificate(key.certificatePem(), clock)).isTrue();
        assertThat(verifier.verify(signed, key.certificatePem(), IDP, SP, ACS, REQUEST, clock))
                .containsEntry("sub", "external-1")
                .containsEntry("email", "member@example.test")
                .containsEntry("email_verified", "true");
    }

    @Test
    void rejectsTamperingUnsignedResponsesWrongRequestAudienceAndExpiredAssertions() throws Exception {
        KeyMaterial key = material(temporary);
        String signed = signed(key, REQUEST, SP, clock.instant().plusSeconds(300), ACS, IDP, clock.instant());
        String tampered = new String(Base64.getDecoder().decode(signed), StandardCharsets.UTF_8)
                .replace("external-1", "external-2");
        assertThatThrownBy(() -> verifier.verify(Base64.getEncoder().encodeToString(tampered.getBytes(StandardCharsets.UTF_8)),
                key.certificatePem(), IDP, SP, ACS, REQUEST, clock)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> verifier.verify(signed, key.certificatePem(), IDP, SP, ACS, "_other", clock))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> verifier.verify(signed(key, REQUEST, "https://other.example.test", clock.instant().plusSeconds(300), ACS, IDP, clock.instant()),
                key.certificatePem(), IDP, SP, ACS, REQUEST, clock)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> verifier.verify(signed(key, REQUEST, SP, clock.instant().minusSeconds(300), ACS, IDP, clock.instant()),
                key.certificatePem(), IDP, SP, ACS, REQUEST, clock)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> verifier.verify(Base64.getEncoder().encodeToString(
                "<Response/>".getBytes(StandardCharsets.UTF_8)), key.certificatePem(), IDP, SP, ACS, REQUEST, clock))
                .isInstanceOf(DomainException.class);
    }

    static KeyMaterial material(Path temporary) throws Exception {
        Path storePath = temporary.resolve("identity-fixture.p12");
        String password = "saml-test-only";
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA", "-storetype", "PKCS12", "-keystore", storePath.toString(),
                "-storepass", password, "-keypass", password, "-dname", "CN=SAML Test Identity",
                "-validity", "3650", "-noprompt").redirectErrorStream(true).start();
        String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("keytool fixture failed: " + result);
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = java.nio.file.Files.newInputStream(storePath)) {
            store.load(input, password.toCharArray());
        }
        X509Certificate certificate = (X509Certificate) store.getCertificate("fixture");
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----";
        return new KeyMaterial((PrivateKey) store.getKey("fixture", password.toCharArray()), pem);
    }

    static String signed(KeyMaterial key, String requestId, String audience, Instant expires,
            String acs, String idp, Instant issued) throws Exception {
        return signedWithIdentity(key, requestId, audience, expires, acs, idp, issued,
                "external-1", "member@example.test", "true");
    }

    static String signedWithIdentity(KeyMaterial key, String requestId, String audience, Instant expires,
            String acs, String idp, Instant issued, String subject, String email, String verified) throws Exception {
        String now = issued.toString();
        String xml = "<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                + "xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ID=\"_response-1\" "
                + "Version=\"2.0\" IssueInstant=\"" + now + "\" Destination=\"" + acs
                + "\" InResponseTo=\"" + requestId + "\">"
                + "<saml:Issuer>" + idp + "</saml:Issuer>"
                + "<samlp:Status><samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/></samlp:Status>"
                + "<saml:Assertion ID=\"_assertion-1\" IssueInstant=\"" + now + "\" Version=\"2.0\">"
                + "<saml:Issuer>" + idp + "</saml:Issuer><saml:Subject>"
                + "<saml:NameID>" + subject + "</saml:NameID>"
                + "<saml:SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
                + "<saml:SubjectConfirmationData Recipient=\"" + acs + "\" InResponseTo=\"" + requestId
                + "\" NotOnOrAfter=\"" + expires + "\"/></saml:SubjectConfirmation></saml:Subject>"
                + "<saml:Conditions NotBefore=\"" + issued.minusSeconds(60) + "\" NotOnOrAfter=\""
                + expires + "\"><saml:AudienceRestriction><saml:Audience>" + audience
                + "</saml:Audience></saml:AudienceRestriction></saml:Conditions>"
                + "<saml:AuthnStatement AuthnInstant=\"" + now + "\"/>"
                + "<saml:AttributeStatement><saml:Attribute Name=\"email\"><saml:AttributeValue>" + email
                + "</saml:AttributeValue></saml:Attribute><saml:Attribute Name=\"email_verified\">"
                + "<saml:AttributeValue>" + verified + "</saml:AttributeValue></saml:Attribute></saml:AttributeStatement>"
                + "</saml:Assertion></samlp:Response>";
        DocumentBuilderFactory builder = DocumentBuilderFactory.newInstance();
        builder.setNamespaceAware(true);
        Document document = builder.newDocumentBuilder().parse(
                new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Element root = document.getDocumentElement();
        XMLSignatureFactory signatures = XMLSignatureFactory.getInstance("DOM");
        Reference reference = signatures.newReference("#_response-1", signatures.newDigestMethod(DigestMethod.SHA256, null),
                List.of(signatures.newTransform(Transform.ENVELOPED, (javax.xml.crypto.dsig.spec.TransformParameterSpec) null)), null, null);
        SignedInfo info = signatures.newSignedInfo(
                signatures.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,
                        (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                signatures.newSignatureMethod(SignatureMethod.RSA_SHA256, null), List.of(reference));
        // SAML Response 的签名位于 Issuer 之后、Status 之前，测试报文遵循协议元素次序。
        DOMSignContext context = new DOMSignContext(key.privateKey(), root,
                root.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:protocol", "Status").item(0));
        context.setIdAttributeNS(root, null, "ID");
        signatures.newXMLSignature(info, null).sign(context);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    record KeyMaterial(PrivateKey privateKey, String certificatePem) {}
}
