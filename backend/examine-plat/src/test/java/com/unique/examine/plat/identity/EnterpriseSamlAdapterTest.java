package com.unique.examine.plat.identity;

import com.sun.net.httpserver.HttpServer;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificate;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnterpriseSamlAdapterTest {
    private KeyPair keys;
    private byte[] certificate;
    private HttpServer server;
    private String base;
    private EnterpriseSamlAdapter adapter;

    @BeforeEach
    void start() throws Exception {
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        certificate = certificate(keys, Instant.parse("2029-01-01T00:00:00Z"),
                Instant.parse("2031-01-01T00:00:00Z"));
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/metadata", exchange -> {
            var body = "<EntityDescriptor xmlns=\"urn:oasis:names:tc:SAML:2.0:metadata\" "
                    + "entityID=\"urn:test:idp\"/>";
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        PlatformSecretResolverFacade secrets = request -> Optional.of(new SecretResolverFacade.ResolvedSecret(certificate));
        adapter = new EnterpriseSamlAdapter(secrets,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), true);
    }

    @AfterEach void stop() { server.stop(0); }

    @Test
    void preflightsMetadataAndValidatesSignedCorrelatedAssertionAndAttributes() throws Exception {
        var provider = provider();
        assertThat(adapter.preflight(provider).successful()).isTrue();

        var response = signedResponse("_request-42", "person@example.test");
        var identity = adapter.validate(provider, response, "_request-42",
                Instant.parse("2030-01-01T00:00:00Z"));

        assertThat(identity.subject()).isEqualTo("saml-user-42");
        assertThat(identity.email()).isEqualTo("person@example.test");
        assertThat(identity.departmentId()).isEqualTo("department-7");
    }

    @Test
    void rejectsSignatureTamperingAndResponseReplayCorrelation() throws Exception {
        var response = signedResponse("_request-42", "person@example.test");
        var tampered = new String(Base64.getDecoder().decode(response), StandardCharsets.UTF_8)
                .replace("person@example.test", "attacker@example.test");

        assertThatThrownBy(() -> adapter.validate(provider(),
                Base64.getEncoder().encodeToString(tampered.getBytes(StandardCharsets.UTF_8)),
                "_request-42", Instant.parse("2030-01-01T00:00:00Z")))
                .isInstanceOf(OidcEnterpriseIdentityClient.IdentityTransportException.class)
                .hasMessage("SAML_ASSERTION_SIGNATURE_INVALID");
        assertThatThrownBy(() -> adapter.validate(provider(), response, "_different-request",
                Instant.parse("2030-01-01T00:00:00Z")))
                .isInstanceOf(OidcEnterpriseIdentityClient.IdentityTransportException.class)
                .hasMessage("SAML_RESPONSE_CORRELATION_INVALID");
    }

    private IdentityApi.Provider provider() {
        return new IdentityApi.Provider(1, "saml-test", "SAML Test", IdentityApi.Protocol.SAML2,
                "urn:test:idp", base + "/sso", null, null, base + "/metadata", "urn:test:sp",
                "env://SAML_CERT", "v1", base + "/callback", "", List.of(),
                Map.of("email", "mail", "departmentId", "department"), true, false, null, null,
                IdentityApi.MfaPolicy.REQUIRED, IdentityApi.Status.PUBLISHED, "PASSED", 0L,
                null, null, null, 0);
    }

    private String signedResponse(String requestId, String email) throws Exception {
        var xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="_response-1"
                  InResponseTo="%s" Destination="%s" Version="2.0" IssueInstant="2030-01-01T00:00:00Z">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/></samlp:Status>
                  <saml:Assertion ID="_assertion-1" Version="2.0" IssueInstant="2030-01-01T00:00:00Z">
                    <saml:Issuer>urn:test:idp</saml:Issuer>
                    <saml:Subject><saml:NameID>saml-user-42</saml:NameID>
                      <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
                        <saml:SubjectConfirmationData InResponseTo="%s" Recipient="%s"
                          NotOnOrAfter="2030-01-01T00:05:00Z"/>
                      </saml:SubjectConfirmation>
                    </saml:Subject>
                    <saml:Conditions NotBefore="2029-12-31T23:58:00Z" NotOnOrAfter="2030-01-01T00:05:00Z">
                      <saml:AudienceRestriction><saml:Audience>urn:test:sp</saml:Audience></saml:AudienceRestriction>
                    </saml:Conditions>
                    <saml:AttributeStatement>
                      <saml:Attribute Name="mail"><saml:AttributeValue>%s</saml:AttributeValue></saml:Attribute>
                      <saml:Attribute Name="department"><saml:AttributeValue>department-7</saml:AttributeValue></saml:Attribute>
                    </saml:AttributeStatement>
                  </saml:Assertion>
                </samlp:Response>
                """.formatted(requestId, base + "/callback", requestId, base + "/callback", email);
        var document = parse(xml.getBytes(StandardCharsets.UTF_8));
        var assertion = (org.w3c.dom.Element) document.getElementsByTagNameNS(
                "urn:oasis:names:tc:SAML:2.0:assertion", "Assertion").item(0);
        assertion.setIdAttribute("ID", true);
        var factory = XMLSignatureFactory.getInstance("DOM");
        Reference reference = factory.newReference("#_assertion-1",
                factory.newDigestMethod(DigestMethod.SHA256, null),
                List.of(factory.newTransform(Transform.ENVELOPED, (javax.xml.crypto.dsig.spec.TransformParameterSpec) null),
                        factory.newTransform(CanonicalizationMethod.EXCLUSIVE,
                                (javax.xml.crypto.dsig.spec.TransformParameterSpec) null)), null, null);
        var signedInfo = factory.newSignedInfo(factory.newCanonicalizationMethod(
                        CanonicalizationMethod.EXCLUSIVE, (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null), List.of(reference));
        var context = new DOMSignContext(keys.getPrivate(), assertion);
        context.setNextSibling(assertion.getFirstChild());
        factory.newXMLSignature(signedInfo, null).sign(context);
        var output = new ByteArrayOutputStream();
        var transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        transformer.transform(new javax.xml.transform.dom.DOMSource(document),
                new javax.xml.transform.stream.StreamResult(output));
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private static byte[] certificate(KeyPair pair, Instant from, Instant until) throws Exception {
        var algorithm = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE);
        var generator = new V3TBSCertificateGenerator();
        generator.setSerialNumber(new ASN1Integer(42)); generator.setSignature(algorithm);
        generator.setIssuer(new X500Name("CN=Identity SAML Test"));
        generator.setStartDate(new Time(Date.from(from))); generator.setEndDate(new Time(Date.from(until)));
        generator.setSubject(new X500Name("CN=Identity SAML Test"));
        generator.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded()));
        TBSCertificate tbs = generator.generateTBSCertificate();
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate()); signer.update(tbs.getEncoded());
        return new Certificate(tbs, algorithm, new DERBitString(signer.sign())).getEncoded();
    }

    private static Document parse(byte[] value) throws Exception {
        var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(value));
    }
}
