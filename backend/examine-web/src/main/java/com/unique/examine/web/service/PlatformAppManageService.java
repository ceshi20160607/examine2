package com.unique.examine.web.service;

import com.unique.examine.app.entity.po.AppClient;
import com.unique.examine.app.entity.po.AppClientCredential;
import com.unique.examine.app.service.IAppClientCredentialService;
import com.unique.examine.app.service.IAppClientService;
import com.unique.examine.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
public class PlatformAppManageService {

    private static final long PLATFORM_SYSTEM_ID = 0L;
    private static final long PLATFORM_TENANT_ID = 0L;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private IAppClientService appClientService;
    @Autowired
    private IAppClientCredentialService appClientCredentialService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public record CreateClientBody(String clientCode,
                                   String clientName,
                                   String contactName,
                                   String contactMobile,
                                   String contactEmail,
                                   String remark) {
    }

    public record CreateClientResult(Long clientId, String accessKey, String secret) {
    }

    public record RotateSecretResult(Long clientId, String accessKey, String secret) {
    }

    public record ClientDetail(AppClient client, String activeAccessKey) {
    }

    public record UpdateClientBody(String clientName,
                                   String contactName,
                                   String contactMobile,
                                   String contactEmail,
                                   String remark) {
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateClient(Long platId, Long clientId, UpdateClientBody body) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (clientId == null) {
            throw new BusinessException(400, "clientId 不能为空");
        }
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.clientName() == null || body.clientName().isBlank()) {
            throw new BusinessException(400, "clientName 不能为空");
        }
        AppClient client = appClientService.getById(clientId);
        if (client == null) {
            throw new BusinessException(404, "client 不存在");
        }
        if (!Objects.equals(client.getSystemId(), PLATFORM_SYSTEM_ID) || !Objects.equals(client.getTenantId(), PLATFORM_TENANT_ID)) {
            throw new BusinessException(403, "仅允许操作平台态 client");
        }
        client.setClientName(body.clientName().trim());
        client.setContactName(trimToNull(body.contactName()));
        client.setContactMobile(trimToNull(body.contactMobile()));
        client.setContactEmail(trimToNull(body.contactEmail()));
        client.setRemark(trimToNull(body.remark()));
        client.setUpdateUserId(platId);
        appClientService.updateById(client);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateClientResult createClient(Long platId, CreateClientBody body) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.clientCode() == null || body.clientCode().isBlank()) {
            throw new BusinessException("clientCode 不能为空");
        }
        if (body.clientName() == null || body.clientName().isBlank()) {
            throw new BusinessException("clientName 不能为空");
        }
        String clientCode = body.clientCode().trim();
        String clientName = body.clientName().trim();

        long existed = appClientService.lambdaQuery()
                .eq(AppClient::getSystemId, PLATFORM_SYSTEM_ID)
                .eq(AppClient::getTenantId, PLATFORM_TENANT_ID)
                .eq(AppClient::getClientCode, clientCode)
                .count();
        if (existed > 0) {
            throw new BusinessException("clientCode 已存在");
        }

        AppClient client = new AppClient();
        client.setSystemId(PLATFORM_SYSTEM_ID);
        client.setTenantId(PLATFORM_TENANT_ID);
        client.setClientCode(clientCode);
        client.setClientName(clientName);
        client.setContactName(trimToNull(body.contactName()));
        client.setContactMobile(trimToNull(body.contactMobile()));
        client.setContactEmail(trimToNull(body.contactEmail()));
        client.setRemark(trimToNull(body.remark()));
        client.setStatus(1);
        client.setCreateUserId(platId);
        client.setUpdateUserId(platId);
        appClientService.save(client);

        String accessKey = generateKey("ak_", 24);
        String secret = generateKey("sk_", 32);
        AppClientCredential cred = new AppClientCredential();
        cred.setSystemId(PLATFORM_SYSTEM_ID);
        cred.setTenantId(PLATFORM_TENANT_ID);
        cred.setClientId(client.getId());
        cred.setAccessKey(accessKey);
        cred.setSecretHash(passwordEncoder.encode(secret));
        cred.setStatus(1);
        cred.setCreateUserId(platId);
        cred.setUpdateUserId(platId);
        appClientCredentialService.save(cred);

        return new CreateClientResult(client.getId(), accessKey, secret);
    }

    public List<AppClient> listClients() {
        return appClientService.lambdaQuery()
                .eq(AppClient::getSystemId, PLATFORM_SYSTEM_ID)
                .eq(AppClient::getTenantId, PLATFORM_TENANT_ID)
                .orderByDesc(AppClient::getUpdateTime)
                .list();
    }

    public ClientDetail getClientDetail(Long clientId) {
        if (clientId == null) {
            throw new BusinessException(400, "clientId 不能为空");
        }
        AppClient client = appClientService.lambdaQuery()
                .eq(AppClient::getSystemId, PLATFORM_SYSTEM_ID)
                .eq(AppClient::getTenantId, PLATFORM_TENANT_ID)
                .eq(AppClient::getId, clientId)
                .last("limit 1")
                .one();
        if (client == null) {
            throw new BusinessException(404, "client 不存在");
        }

        AppClientCredential active = appClientCredentialService.lambdaQuery()
                .eq(AppClientCredential::getSystemId, PLATFORM_SYSTEM_ID)
                .eq(AppClientCredential::getTenantId, PLATFORM_TENANT_ID)
                .eq(AppClientCredential::getClientId, clientId)
                .eq(AppClientCredential::getStatus, 1)
                .orderByDesc(AppClientCredential::getCreateTime)
                .last("limit 1")
                .one();
        return new ClientDetail(client, active == null ? null : active.getAccessKey());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateClientStatus(Long platId, Long clientId, Integer status) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (clientId == null) {
            throw new BusinessException(400, "clientId 不能为空");
        }
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }
        AppClient client = appClientService.getById(clientId);
        if (client == null) {
            throw new BusinessException(404, "client 不存在");
        }
        if (!Objects.equals(client.getSystemId(), PLATFORM_SYSTEM_ID) || !Objects.equals(client.getTenantId(), PLATFORM_TENANT_ID)) {
            throw new BusinessException(403, "仅允许操作平台态 client");
        }
        client.setStatus(status);
        client.setUpdateUserId(platId);
        appClientService.updateById(client);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteClient(Long platId, Long clientId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (clientId == null) {
            throw new BusinessException(400, "clientId 不能为空");
        }
        AppClient client = appClientService.getById(clientId);
        if (client == null) {
            throw new BusinessException(404, "client 不存在");
        }
        if (!Objects.equals(client.getSystemId(), PLATFORM_SYSTEM_ID) || !Objects.equals(client.getTenantId(), PLATFORM_TENANT_ID)) {
            throw new BusinessException(403, "仅允许操作平台态 client");
        }
        appClientService.removeById(clientId);
        appClientCredentialService.lambdaUpdate()
                .eq(AppClientCredential::getClientId, clientId)
                .set(AppClientCredential::getStatus, 2)
                .set(AppClientCredential::getUpdateUserId, platId)
                .set(AppClientCredential::getUpdateTime, LocalDateTime.now())
                .update();
    }

    @Transactional(rollbackFor = Exception.class)
    public RotateSecretResult rotateSecret(Long platId, Long clientId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        if (clientId == null) {
            throw new BusinessException(400, "clientId 不能为空");
        }
        AppClient client = appClientService.getById(clientId);
        if (client == null) {
            throw new BusinessException(404, "client 不存在");
        }
        if (!Objects.equals(client.getSystemId(), PLATFORM_SYSTEM_ID) || !Objects.equals(client.getTenantId(), PLATFORM_TENANT_ID)) {
            throw new BusinessException(403, "仅允许操作平台态 client");
        }

        appClientCredentialService.lambdaUpdate()
                .eq(AppClientCredential::getSystemId, PLATFORM_SYSTEM_ID)
                .eq(AppClientCredential::getTenantId, PLATFORM_TENANT_ID)
                .eq(AppClientCredential::getClientId, clientId)
                .eq(AppClientCredential::getStatus, 1)
                .set(AppClientCredential::getStatus, 2)
                .set(AppClientCredential::getUpdateUserId, platId)
                .set(AppClientCredential::getUpdateTime, LocalDateTime.now())
                .update();

        String accessKey = generateKey("ak_", 24);
        String secret = generateKey("sk_", 32);
        AppClientCredential cred = new AppClientCredential();
        cred.setSystemId(PLATFORM_SYSTEM_ID);
        cred.setTenantId(PLATFORM_TENANT_ID);
        cred.setClientId(clientId);
        cred.setAccessKey(accessKey);
        cred.setSecretHash(passwordEncoder.encode(secret));
        cred.setStatus(1);
        cred.setCreateUserId(platId);
        cred.setUpdateUserId(platId);
        appClientCredentialService.save(cred);

        return new RotateSecretResult(clientId, accessKey, secret);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String generateKey(String prefix, int randomBytes) {
        byte[] b = new byte[randomBytes];
        SECURE_RANDOM.nextBytes(b);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}

