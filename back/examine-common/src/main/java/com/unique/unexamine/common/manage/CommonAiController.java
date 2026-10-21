package com.unique.unexamine.common.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.common.base.entity.UnCommonAi;
import com.unique.unexamine.common.base.service.IUnCommonAiService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/** 公共 AI 服务配置；凭据只接受写入并以 AES-GCM 加密存储。 */
@Tag(name = "AI 配置", description = "按范围管理 AI 配置和凭据。")
@RestController
@RequestMapping("/api/common/ai-configs")
public class CommonAiController {
    private final IUnCommonAiService configs;
    private final ResourceScopeAccess scope;
    private final SecureRandom random = new SecureRandom();

    public CommonAiController(IUnCommonAiService configs, ResourceScopeAccess scope) {
        this.configs = configs;
        this.scope = scope;
    }

    /** 配置查询只返回有无凭据，不返回密文或明文。 */
    @Operation(summary = "查询列表（AI 配置）")
    @GetMapping
    public IPage<AiView> list(@RequestParam String scopeType, @RequestParam long scopeId,
                              @RequestParam(defaultValue = "1") long page,
                              @RequestParam(defaultValue = "20") long size,
                              @RequestParam(defaultValue = "configCode") String sort,
                              HttpServletRequest request) {
        scope.admin(scopeType, scopeId, request);
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
        LambdaQueryWrapper<UnCommonAi> query = new LambdaQueryWrapper<UnCommonAi>()
                .eq(UnCommonAi::getScopeType, scopeType).eq(UnCommonAi::getScopeId, scopeId);
        switch (sort) {
            case "configCode" -> query.orderByAsc(UnCommonAi::getConfigCode);
            case "providerName" -> query.orderByAsc(UnCommonAi::getProviderName);
            case "createTime" -> query.orderByDesc(UnCommonAi::getCreateTime);
            default -> throw bad("不支持的AI配置排序字段");
        }
        return configs.page(new Page<>(page, size), query).convert(this::view);
    }

    /** 新建时在范围内检查编码重复，存在凭据时要求部署方提供加密密钥。 */
    @Operation(summary = "新建（AI 配置）")
    @PostMapping
    public AiView create(@Valid @RequestBody AiInput input, HttpServletRequest request) {
        scope.admin(input.scopeType(), input.scopeId(), request);
        ensureCode(input.scopeType(), input.scopeId(), input.configCode(), null);
        UnCommonAi item = new UnCommonAi();
        item.setScopeType(input.scopeType());
        item.setScopeId(input.scopeId());
        item.setConfigCode(input.configCode().trim());
        item.setProviderName(input.providerName().trim());
        item.setModelName(input.modelName().trim());
        item.setApiBaseUrl(input.apiBaseUrl());
        item.setSecretCiphertext(encryptOrNull(input.credential()));
        item.setStatus((byte) 1);
        configs.save(item);
        return view(item);
    }

    /** 修改时不变更资源范围；未提交凭据表示保留已有密文。 */
    @Operation(summary = "修改（AI 配置）")
    @PutMapping("/{id}")
    public AiView update(@PathVariable long id, @Valid @RequestBody AiUpdateInput input,
                         HttpServletRequest request) {
        UnCommonAi item = config(id);
        scope.admin(item.getScopeType(), item.getScopeId(), request);
        ensureCode(item.getScopeType(), item.getScopeId(), input.configCode(), id);
        item.setConfigCode(input.configCode().trim());
        item.setProviderName(input.providerName().trim());
        item.setModelName(input.modelName().trim());
        item.setApiBaseUrl(input.apiBaseUrl());
        if (input.credential() != null) item.setSecretCiphertext(encryptOrNull(input.credential()));
        configs.updateById(item);
        return view(item);
    }

    /** 停用配置不删除历史设置和加密凭据。 */
    @Operation(summary = "修改状态（AI 配置）")
    @PutMapping("/{id}/status")
    public AiView status(@PathVariable long id, @RequestBody StatusInput input,
                         HttpServletRequest request) {
        UnCommonAi item = config(id);
        scope.admin(item.getScopeType(), item.getScopeId(), request);
        if (input.status() != 0 && input.status() != 1) throw bad("状态只能是0或1");
        item.setStatus(input.status());
        configs.updateById(item);
        return view(item);
    }

    private String encryptOrNull(String credential) {
        if (credential == null) return null;
        if (credential.isBlank()) throw bad("AI凭据不能为空");
        String encoded = System.getenv("APP_AI_ENCRYPTION_KEY");
        if (encoded == null || encoded.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "缺少APP_AI_ENCRYPTION_KEY");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI加密密钥格式无效");
        }
        if (key.length != 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI加密密钥必须是32字节Base64");
        }
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(credential.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception failure) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI凭据加密失败");
        }
    }

    private void ensureCode(String scopeType, long scopeId, String code, Long exceptId) {
        LambdaQueryWrapper<UnCommonAi> query = new LambdaQueryWrapper<UnCommonAi>()
                .eq(UnCommonAi::getScopeType, scopeType).eq(UnCommonAi::getScopeId, scopeId)
                .eq(UnCommonAi::getConfigCode, code.trim());
        if (exceptId != null) query.ne(UnCommonAi::getId, exceptId);
        if (configs.count(query) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "AI配置编码已存在");
    }

    private UnCommonAi config(long id) {
        UnCommonAi item = configs.getById(id);
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI配置不存在");
        return item;
    }

    private AiView view(UnCommonAi item) {
        return new AiView(item.getId(), item.getScopeType(), item.getScopeId(), item.getConfigCode(),
                item.getProviderName(), item.getModelName(), item.getApiBaseUrl(), item.getStatus(),
                item.getSecretCiphertext() != null && !item.getSecretCiphertext().isBlank());
    }

    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }

    /** 创建请求中的凭据仅用于加密，不会原样保存或回显。 */
    public record AiInput(@NotBlank String scopeType, long scopeId,
                          @NotBlank @Size(max = 64) String configCode,
                          @NotBlank @Size(max = 100) String providerName,
                          @NotBlank @Size(max = 100) String modelName,
                          String apiBaseUrl, String credential) {}
    public record AiUpdateInput(@NotBlank @Size(max = 64) String configCode,
                                @NotBlank @Size(max = 100) String providerName,
                                @NotBlank @Size(max = 100) String modelName,
                                String apiBaseUrl, String credential) {}
    public record AiView(Long id, String scopeType, Long scopeId, String configCode,
                         String providerName, String modelName, String apiBaseUrl,
                         Byte status, boolean credentialConfigured) {}
    public record StatusInput(byte status) {}
}
