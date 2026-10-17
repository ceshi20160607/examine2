package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验 SP 发起的 SAML2 浏览器 POST 响应。仅接纳由配置证书签署的整个 Response，
 * 确保后续使用的 Assertion 在同一签名覆盖范围内，不接纳未关联请求的 IdP 主动登录。
 */
@Component
public class SamlResponseVerifier {
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String DSIG = XMLSignature.XMLNS;
    private static final String SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";
    private static final String BEARER = "urn:oasis:names:tc:SAML:2.0:cm:bearer";
    private static final int MAX_RESPONSE_LENGTH = 196_608;
    private static final long CLOCK_SKEW_SECONDS = 120;

    /** 返回签名断言内的属性；所有业务身份映射只可读取这个结果。 */
    public Map<String, Object> verify(String encodedResponse, String certificatePem, String idpIssuer,
            String spEntityId, String acsUri, String requestId, Clock clock) {
        if (encodedResponse == null || encodedResponse.length() > MAX_RESPONSE_LENGTH) {
            throw invalid("SAML 响应缺失或过大");
        }
        try {
            byte[] xml = Base64.getDecoder().decode(encodedResponse);
            Document document = secureDocument(xml);
            Element response = document.getDocumentElement();
            requireName(response, PROTOCOL, "Response");
            requireEqual("2.0", requiredAttribute(response, "Version"));
            String responseId = requiredAttribute(response, "ID");
            requireUniqueIds(response);
            requireEqual(acsUri, requiredAttribute(response, "Destination"));
            requireEqual(requestId, requiredAttribute(response, "InResponseTo"));
            requireEqual(idpIssuer, directText(response, ASSERTION, "Issuer"));
            requireRecent(requiredAttribute(response, "IssueInstant"), clock);
            Element status = onlyDirect(response, PROTOCOL, "Status");
            requireEqual(SUCCESS, requiredAttribute(onlyDirect(status, PROTOCOL, "StatusCode"), "Value"));

            // 使用平台固定的证书公钥，忽略响应内可被攻击者替换的 KeyInfo。
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.US_ASCII)));
            certificate.checkValidity(Date.from(clock.instant()));
            Element signatureNode = onlyDirect(response, DSIG, "Signature");
            DOMValidateContext context = new DOMValidateContext(certificate.getPublicKey(), signatureNode);
            context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
            context.setIdAttributeNS(response, null, "ID");
            XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
            List<?> references = signature.getSignedInfo().getReferences();
            if (references.size() != 1 || !(references.getFirst() instanceof Reference reference)
                    || !("#" + responseId).equals(reference.getURI()) || !signature.validate(context)) {
                throw invalid("SAML 响应签名无效或覆盖范围不正确");
            }

            Element assertion = onlyDirect(response, ASSERTION, "Assertion");
            requireEqual("2.0", requiredAttribute(assertion, "Version"));
            requireEqual(idpIssuer, directText(assertion, ASSERTION, "Issuer"));
            requireRecent(requiredAttribute(assertion, "IssueInstant"), clock);
            Element subject = onlyDirect(assertion, ASSERTION, "Subject");
            String externalSubject = directText(subject, ASSERTION, "NameID");
            if (externalSubject.isBlank()) throw invalid("SAML 外部用户标识为空");
            Element confirmation = onlyDirect(subject, ASSERTION, "SubjectConfirmation");
            requireEqual(BEARER, requiredAttribute(confirmation, "Method"));
            Element confirmationData = onlyDirect(confirmation, ASSERTION, "SubjectConfirmationData");
            requireEqual(acsUri, requiredAttribute(confirmationData, "Recipient"));
            requireEqual(requestId, requiredAttribute(confirmationData, "InResponseTo"));
            requireNotExpired(requiredAttribute(confirmationData, "NotOnOrAfter"), clock);

            Element conditions = onlyDirect(assertion, ASSERTION, "Conditions");
            requireNotBefore(requiredAttribute(conditions, "NotBefore"), clock);
            requireNotExpired(requiredAttribute(conditions, "NotOnOrAfter"), clock);
            Element audienceRestriction = onlyDirect(conditions, ASSERTION, "AudienceRestriction");
            requireEqual(spEntityId, directText(audienceRestriction, ASSERTION, "Audience"));
            onlyDirect(assertion, ASSERTION, "AuthnStatement");

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", externalSubject);
            for (Element statement : direct(assertion, ASSERTION, "AttributeStatement")) {
                for (Element attribute : direct(statement, ASSERTION, "Attribute")) {
                    String name = requiredAttribute(attribute, "Name");
                    String value = directText(attribute, ASSERTION, "AttributeValue");
                    if (attributes.putIfAbsent(name, value) != null) throw invalid("SAML 属性重复");
                }
            }
            return Map.copyOf(attributes);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("SAML 响应无法安全验证");
        }
    }

    /** 发布预检需要解析真实 X.509 证书，不能只检查 PEM 文本边界。 */
    public boolean validCertificate(String certificatePem, Clock clock) {
        if (certificatePem == null || certificatePem.isBlank()) return false;
        try {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.US_ASCII)));
            certificate.checkValidity(Date.from(clock.instant()));
            return "RSA".equalsIgnoreCase(certificate.getPublicKey().getAlgorithm());
        } catch (Exception exception) {
            return false;
        }
    }

    private Document secureDocument(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    /** 拒绝重复 XML ID，防止验签节点和业务读取节点被包装成不同内容。 */
    private void requireUniqueIds(Element root) {
        Set<String> ids = new HashSet<>();
        List<Element> pending = new ArrayList<>(List.of(root));
        while (!pending.isEmpty()) {
            Element element = pending.removeLast();
            if (element.hasAttribute("ID") && !ids.add(element.getAttribute("ID"))) throw invalid("SAML ID 重复");
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element nested) pending.add(nested);
            }
        }
    }

    private List<Element> direct(Element parent, String namespace, String name) {
        List<Element> found = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && namespace.equals(element.getNamespaceURI())
                    && name.equals(element.getLocalName())) found.add(element);
        }
        return found;
    }

    private Element onlyDirect(Element parent, String namespace, String name) {
        List<Element> found = direct(parent, namespace, name);
        if (found.size() != 1) throw invalid("SAML 响应结构缺失或重复：" + name);
        return found.getFirst();
    }

    private String directText(Element parent, String namespace, String name) {
        String value = onlyDirect(parent, namespace, name).getTextContent();
        return value == null ? "" : value.strip();
    }

    private void requireName(Element element, String namespace, String name) {
        if (!namespace.equals(element.getNamespaceURI()) || !name.equals(element.getLocalName())) {
            throw invalid("SAML 根节点无效");
        }
    }

    private String requiredAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) throw invalid("SAML 属性缺失：" + name);
        return value;
    }

    private void requireEqual(String expected, String actual) {
        if (expected == null || !expected.equals(actual)) throw invalid("SAML 请求或受众不匹配");
    }

    private void requireRecent(String value, Clock clock) {
        Instant issueTime = Instant.parse(value);
        if (issueTime.isAfter(clock.instant().plusSeconds(CLOCK_SKEW_SECONDS))
                || issueTime.isBefore(clock.instant().minusSeconds(300 + CLOCK_SKEW_SECONDS))) {
            throw invalid("SAML 响应签发时间无效");
        }
    }

    private void requireNotBefore(String value, Clock clock) {
        if (Instant.parse(value).isAfter(clock.instant().plusSeconds(CLOCK_SKEW_SECONDS))) {
            throw invalid("SAML 断言尚未生效");
        }
    }

    private void requireNotExpired(String value, Clock clock) {
        if (!Instant.parse(value).isAfter(clock.instant().minusSeconds(CLOCK_SKEW_SECONDS))) {
            throw invalid("SAML 断言已过期");
        }
    }

    private DomainException invalid(String message) {
        return new DomainException("SSO_SAML_RESPONSE_INVALID", message, HttpStatus.UNAUTHORIZED);
    }
}
