package com.unique.unexamine.operations.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.operations.base.entity.OpsSecretRef;
import com.unique.unexamine.operations.base.entity.OpsSecretRotation;
import com.unique.unexamine.operations.base.service.OpsSecretRefBaseService;
import com.unique.unexamine.operations.base.service.OpsSecretRotationBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * 运行组件只通过受控引用读取指定版本的密钥材料。调用方不得持久化、记录或返回解密后的明文。
 */
@Service
public class OperationsSecretResolver {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final OpsSecretRefBaseService refs;
    private final OpsSecretRotationBaseService rotations;
    private final OperationsSecretCipher cipher;
    private final ObjectMapper objectMapper;

    public OperationsSecretResolver(
            OpsSecretRefBaseService refs,
            OpsSecretRotationBaseService rotations,
            OperationsSecretCipher cipher,
            ObjectMapper objectMapper) {
        this.refs = refs;
        this.rotations = rotations;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    /** 只验证引用身份和版本，不解密材料，可用于发布检查和安全目录。 */
    @Transactional(readOnly = true)
    public SecretReference requireWebhookReference(Long systemId, String referencePath) {
        if (systemId == null || referencePath == null || referencePath.isBlank()) {
            throw invalid("WEBHOOK_SECRET_REFERENCE_INVALID", "Webhook 签名密钥引用无效");
        }
        String prefix = "ops-secret://system/" + systemId + "/";
        String normalized = referencePath.strip();
        if (!normalized.startsWith(prefix)) {
            throw invalid("WEBHOOK_SECRET_REFERENCE_SCOPE_INVALID", "Webhook 签名密钥不属于当前系统");
        }
        String suffix = normalized.substring(prefix.length());
        int separator = suffix.lastIndexOf('/');
        if (separator < 1 || separator == suffix.length() - 1) {
            throw invalid("WEBHOOK_SECRET_REFERENCE_INVALID", "Webhook 签名密钥引用缺少版本");
        }
        String code = suffix.substring(0, separator);
        String version = suffix.substring(separator + 1);
        OpsSecretRef ref = refs.selectList(new LambdaQueryWrapper<OpsSecretRef>()
                        .eq(OpsSecretRef::getSystemId, systemId)
                        .eq(OpsSecretRef::getSecretCode, code).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (ref == null || ref.getProvider() == null || !ref.getProvider().endsWith("_WEBHOOK")) {
            throw invalid("WEBHOOK_SECRET_REFERENCE_NOT_FOUND", "Webhook 签名密钥引用不存在或类型不正确");
        }
        OpsSecretRotation rotation = version(ref.getId(), version);
        if (rotation == null || !Objects.equals(rotation.getSecretRefId(), ref.getId())
                || !java.util.Set.of("ACTIVE", "DISABLED").contains(rotation.getStatus())) {
            throw invalid("WEBHOOK_SECRET_VERSION_NOT_AVAILABLE", "Webhook 签名密钥版本不可用");
        }
        return new SecretReference(ref.getId(), ref.getSecretCode(), version, normalized);
    }

    /** 解密后的明文仅在一次签名计算的调用栈内存在。 */
    @Transactional(readOnly = true)
    public String resolveWebhookMaterial(Long systemId, String referencePath) {
        SecretReference reference = requireWebhookReference(systemId, referencePath);
        OpsSecretRotation rotation = version(reference.refId(), reference.version());
        Map<String, Object> evidence = readMap(rotation == null ? null : rotation.getVerificationJson());
        Object materialReference = evidence.get("materialReference");
        if (!(materialReference instanceof String encrypted) || encrypted.isBlank()) {
            throw invalid("WEBHOOK_SECRET_MATERIAL_NOT_AVAILABLE", "Webhook 签名密钥材料不可用");
        }
        try {
            return cipher.decrypt(encrypted, reference.refId(), reference.version());
        } catch (RuntimeException failure) {
            throw invalid("WEBHOOK_SECRET_MATERIAL_INVALID", "Webhook 签名密钥材料无法读取");
        }
    }

    private OpsSecretRotation version(Long refId, String version) {
        return rotations.selectList(new LambdaQueryWrapper<OpsSecretRotation>()
                        .eq(OpsSecretRotation::getSecretRefId, refId)
                        .eq(OpsSecretRotation::getToVersion, version)
                        .orderByDesc(OpsSecretRotation::getId).last("limit 1"))
                .stream().findFirst().orElse(null);
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException failure) {
            throw invalid("WEBHOOK_SECRET_MATERIAL_INVALID", "Webhook 签名密钥材料无法读取");
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record SecretReference(Long refId, String secretCode, String version, String referencePath) { }
}
