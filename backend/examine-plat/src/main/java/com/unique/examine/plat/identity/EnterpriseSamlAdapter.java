package com.unique.examine.plat.identity;

import com.unique.examine.core.api.PlatformSecretResolverFacade;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EnterpriseSamlAdapter {
    private static final String ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String DSIG_NS = XMLSignature.XMLNS;
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(2);
    private final PlatformSecretResolverFacade secrets;
    private final HttpClient http;
    private final boolean allowLoopback;

    EnterpriseSamlAdapter(PlatformSecretResolverFacade secrets, HttpClient http, boolean allowLoopback) {
        this.secrets = secrets;
        this.http = http;
        this.allowLoopback = allowLoopback;
    }

    EnterpriseIdentityClient.Preflight preflight(IdentityApi.Provider p) {
        var checks = new ArrayList<String>();
        try {
            IdentityNetworkPolicy.https(p.authorizationEndpoint(), "saml-sso", allowLoopback);
            checks.add("saml-sso-endpoint");
            var metadata = IdentityNetworkPolicy.https(p.directoryEndpoint(), "saml-metadata", allowLoopback);
            var response = http.send(HttpRequest.newBuilder(metadata).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length > 1_000_000) {
                throw failure("SAML_METADATA_UNREACHABLE");
            }
            var document = parse(response.body());
            var root = document.getDocumentElement();
            if (!"EntityDescriptor".equals(root.getLocalName())
                    || !p.issuerUri().equals(root.getAttribute("entityID"))) {
                throw failure("SAML_METADATA_ENTITY_MISMATCH");
            }
            checks.add("saml-metadata-entity");
            certificate(p); checks.add("saml-signing-certificate-resolved");
            return new EnterpriseIdentityClient.Preflight(true, null, List.copyOf(checks));
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            return new EnterpriseIdentityClient.Preflight(false, e.code, List.copyOf(checks));
        } catch (Exception e) {
            return new EnterpriseIdentityClient.Preflight(false, "SAML_PREFLIGHT_FAILED", List.copyOf(checks));
        }
    }

    IdentityApi.ExternalIdentity validate(IdentityApi.Provider p, String encodedResponse,
                                          String requestId, Instant now) {
        try {
            byte[] xml;
            try {
                xml = Base64.getMimeDecoder().decode(encodedResponse);
            } catch (IllegalArgumentException malformed) {
                throw failure("SAML_RESPONSE_BASE64_INVALID");
            }
            if (xml.length == 0 || xml.length > 1_000_000) throw failure("SAML_RESPONSE_SIZE_INVALID");
            var document = parse(xml);
            var response = document.getDocumentElement();
            if (!PROTOCOL_NS.equals(response.getNamespaceURI()) || !"Response".equals(response.getLocalName())) {
                throw failure("SAML_RESPONSE_ROOT_INVALID");
            }
            if (!p.callbackUri().equals(response.getAttribute("Destination"))
                    || !requestId.equals(response.getAttribute("InResponseTo"))) {
                throw failure("SAML_RESPONSE_CORRELATION_INVALID");
            }
            var status = first(response, PROTOCOL_NS, "StatusCode");
            if (status == null || !"urn:oasis:names:tc:SAML:2.0:status:Success"
                    .equals(status.getAttribute("Value"))) {
                throw failure("SAML_RESPONSE_STATUS_INVALID");
            }
            var assertions = response.getElementsByTagNameNS(ASSERTION_NS, "Assertion");
            if (assertions.getLength() != 1) throw failure("SAML_ASSERTION_COUNT_INVALID");
            var assertion = (Element) assertions.item(0);
            registerUniqueIds(document);
            var signingCertificate = certificate(p);
            validateCertificateValidity(signingCertificate, now);
            validateSignature(assertion, signingCertificate);
            validateIssuerAudienceAndTime(assertion, p, now);
            validateSubjectConfirmation(assertion, p.callbackUri(), requestId, now);
            return identity(assertion, p);
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcEnterpriseIdentityClient.IdentityTransportException("SAML_ASSERTION_INVALID", e);
        }
    }

    private void validateSignature(Element assertion, X509Certificate certificate) throws Exception {
        var signatures = assertion.getElementsByTagNameNS(DSIG_NS, "Signature");
        if (signatures.getLength() != 1 || signatures.item(0).getParentNode() != assertion) {
            throw failure("SAML_ASSERTION_SIGNATURE_REQUIRED");
        }
        var context = new DOMValidateContext(certificate.getPublicKey(), signatures.item(0));
        context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
        var signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
        var assertionId = assertion.getAttribute("ID");
        if (assertionId.isBlank() || signature.getSignedInfo().getReferences().size() != 1
                || !(signature.getSignedInfo().getReferences().getFirst() instanceof javax.xml.crypto.dsig.Reference ref)
                || !("#" + assertionId).equals(ref.getURI())
                || !SignatureMethod.RSA_SHA256.equals(signature.getSignedInfo().getSignatureMethod().getAlgorithm())
                || !DigestMethod.SHA256.equals(ref.getDigestMethod().getAlgorithm())) {
            throw failure("SAML_SIGNATURE_REFERENCE_INVALID");
        }
        if (!signature.validate(context)) throw failure("SAML_ASSERTION_SIGNATURE_INVALID");
    }

    private static void validateCertificateValidity(X509Certificate certificate, Instant now) {
        try {
            certificate.checkValidity(java.util.Date.from(now));
        } catch (Exception invalid) {
            throw failure("SAML_CERTIFICATE_TIME_INVALID");
        }
    }

    private void validateIssuerAudienceAndTime(Element assertion, IdentityApi.Provider p, Instant now) {
        var issuer = firstText(assertion, ASSERTION_NS, "Issuer");
        if (!p.issuerUri().equals(issuer)) throw failure("SAML_ISSUER_INVALID");
        var conditions = first(assertion, ASSERTION_NS, "Conditions");
        if (conditions == null) throw failure("SAML_CONDITIONS_MISSING");
        var notBefore = instant(conditions.getAttribute("NotBefore"), "SAML_NOT_BEFORE_INVALID");
        var notAfter = instant(conditions.getAttribute("NotOnOrAfter"), "SAML_EXPIRY_INVALID");
        if (now.plus(CLOCK_SKEW).isBefore(notBefore) || !now.minus(CLOCK_SKEW).isBefore(notAfter)) {
            throw failure("SAML_ASSERTION_EXPIRED");
        }
        var audiences = assertion.getElementsByTagNameNS(ASSERTION_NS, "Audience");
        boolean accepted = false;
        for (int i = 0; i < audiences.getLength(); i++) {
            if (p.clientId().equals(audiences.item(i).getTextContent().strip())) accepted = true;
        }
        if (!accepted) throw failure("SAML_AUDIENCE_INVALID");
    }

    private void validateSubjectConfirmation(Element assertion, String recipient,
                                               String requestId, Instant now) {
        var values = assertion.getElementsByTagNameNS(ASSERTION_NS, "SubjectConfirmationData");
        if (values.getLength() != 1) throw failure("SAML_SUBJECT_CONFIRMATION_INVALID");
        var value = (Element) values.item(0);
        if (!recipient.equals(value.getAttribute("Recipient"))
                || !requestId.equals(value.getAttribute("InResponseTo"))
                || !now.minus(CLOCK_SKEW).isBefore(instant(value.getAttribute("NotOnOrAfter"),
                "SAML_SUBJECT_EXPIRY_INVALID"))) {
            throw failure("SAML_SUBJECT_CONFIRMATION_INVALID");
        }
    }

    private IdentityApi.ExternalIdentity identity(Element assertion, IdentityApi.Provider p) {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("NameID", firstText(assertion, ASSERTION_NS, "NameID"));
        var attributes = assertion.getElementsByTagNameNS(ASSERTION_NS, "Attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            var attribute = (Element) attributes.item(i);
            var name = attribute.getAttribute("Name");
            var values = attribute.getElementsByTagNameNS(ASSERTION_NS, "AttributeValue");
            if (!name.isBlank() && values.getLength() > 0) {
                claims.put(name, values.getLength() == 1 ? values.item(0).getTextContent().strip()
                        : java.util.stream.IntStream.range(0, values.getLength())
                        .mapToObj(index -> values.item(index).getTextContent().strip()).toList());
            }
        }
        var mapping = p.attributeMapping();
        var subject = string(claims.get(mapping.getOrDefault("externalUserId", "NameID")));
        if (subject == null || subject.isBlank()) throw failure("IDENTITY_SUBJECT_MISSING");
        return new IdentityApi.ExternalIdentity(subject,
                string(claims.get(mapping.getOrDefault("email", "email"))),
                string(claims.get(mapping.getOrDefault("displayName", "displayName"))),
                string(claims.get(mapping.getOrDefault("mobile", "mobile"))),
                string(claims.get(mapping.getOrDefault("employeeNo", "employeeNo"))),
                string(claims.get(mapping.getOrDefault("departmentId", "departmentId"))),
                Map.copyOf(claims));
    }

    private X509Certificate certificate(IdentityApi.Provider p) {
        if (p.secretRef() == null || p.secretVersion() == null) throw failure("IDENTITY_SECRET_REF_MISSING");
        byte[] bytes = null;
        try (var value = secrets.resolve(new PlatformSecretResolverFacade.SecretRequest(p.secretRef()))
                .orElseThrow(() -> failure("IDENTITY_SECRET_UNRESOLVED"))) {
            bytes = value.copyBytes();
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(bytes));
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcEnterpriseIdentityClient.IdentityTransportException("SAML_CERTIFICATE_INVALID", e);
        } finally {
            if (bytes != null) java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private static Document parse(byte[] xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static void registerUniqueIds(Document document) {
        var ids = new HashSet<String>();
        var elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            var element = (Element) elements.item(i);
            if (element.hasAttribute("ID")) {
                var id = element.getAttribute("ID");
                if (id.isBlank() || !ids.add(id)) throw failure("SAML_DUPLICATE_ID");
                element.setIdAttribute("ID", true);
            }
        }
    }

    private static Element first(Element parent, String ns, String name) {
        var values = parent.getElementsByTagNameNS(ns, name);
        return values.getLength() == 0 ? null : (Element) values.item(0);
    }
    private static String firstText(Element parent, String ns, String name) {
        var value = first(parent, ns, name); return value == null ? null : value.getTextContent().strip();
    }
    private static Instant instant(String value, String code) {
        try { return Instant.parse(value); }
        catch (Exception e) { throw failure(code); }
    }
    private static String string(Object value) {
        if (value instanceof List<?> list) return list.isEmpty() ? null : String.valueOf(list.getFirst());
        return value == null ? null : String.valueOf(value);
    }
    private static OidcEnterpriseIdentityClient.IdentityTransportException failure(String code) {
        return new OidcEnterpriseIdentityClient.IdentityTransportException(code);
    }
}
