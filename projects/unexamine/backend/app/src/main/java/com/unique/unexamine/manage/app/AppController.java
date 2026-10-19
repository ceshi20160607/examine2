package com.unique.unexamine.manage.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.base.entity.UnApp;
import com.unique.unexamine.base.entity.UnAppEvent;
import com.unique.unexamine.base.entity.UnSysSystem;
import com.unique.unexamine.base.entity.UnSysTenant;
import com.unique.unexamine.base.mapper.UnAppEventMapper;
import com.unique.unexamine.base.mapper.UnAppMapper;
import com.unique.unexamine.base.mapper.UnSysSystemMapper;
import com.unique.unexamine.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.manage.common.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/** 管理业务系统及租户范围内的应用凭据和事件订阅。 */
@RestController
@RequestMapping("/api/apps")
public class AppController {
    private final UnAppMapper apps;
    private final UnAppEventMapper events;
    private final UnSysSystemMapper systems;
    private final UnSysTenantMapper tenants;
    private final SessionAccess access;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AppController(UnAppMapper apps, UnAppEventMapper events, UnSysSystemMapper systems,
                         UnSysTenantMapper tenants, SessionAccess access) {
        this.apps = apps;
        this.events = events;
        this.systems = systems;
        this.tenants = tenants;
        this.access = access;
    }

    /** 应用密钥只在创建或轮换时返回一次，列表和详情均不暴露摘要。 */
    @PostMapping
    public CreatedApp create(@Valid @RequestBody AppInput input, HttpServletRequest request) {
        authorize(request, input.systemId(), input.tenantId(), true);
        UnApp app = new UnApp();
        app.setSystemId(input.systemId());
        app.setTenantId(input.tenantId());
        app.setAppKey(input.appKey().trim());
        app.setAppName(input.appName().trim());
        app.setStatus((byte) 1);
        String secret = newSecret();
        app.setSecretHash(encoder.encode(secret));
        apps.insert(app);
        return new CreatedApp(view(app), secret);
    }

    /** 凭据验证仅确认应用自身身份与所属范围，不签发业务接口访问令牌。 */
    @PostMapping("/verify")
    public AppIdentity verify(@Valid @RequestBody VerifyInput input) {
        UnApp app = apps.selectOne(new LambdaQueryWrapper<UnApp>()
                .eq(UnApp::getAppKey, input.appKey().trim()));
        if (app == null || !app.getAppKey().equals(input.appKey().trim())
                || input.secret().length() != 43 || app.getSecretHash() == null
                || !encoder.matches(input.secret(), app.getSecretHash())
                || app.getStatus() == null || app.getStatus() != 1) {
            throw invalidCredential();
        }
        UnSysSystem system = systems.selectById(app.getSystemId());
        if (system == null || system.getStatus() == null || system.getStatus() != 1) {
            throw invalidCredential();
        }
        if (app.getTenantId() != null) {
            UnSysTenant tenant = tenants.selectById(app.getTenantId());
            if (tenant == null || !app.getSystemId().equals(tenant.getSystemId())
                    || tenant.getStatus() == null || tenant.getStatus() != 1) {
                throw invalidCredential();
            }
        }
        return new AppIdentity(app.getId(), app.getSystemId(), app.getTenantId(),
                app.getAppKey(), app.getAppName());
    }

    /** 只有所属范围管理员可轮换；数据库写入新摘要后，旧密钥即刻失效。 */
    @PostMapping("/{appId}/secret/rotate")
    public CreatedApp rotateSecret(@PathVariable long appId, HttpServletRequest request) {
        UnApp app = requireApp(appId);
        authorize(request, app.getSystemId(), app.getTenantId(), true);
        String secret = newSecret();
        app.setSecretHash(encoder.encode(secret));
        apps.updateById(app);
        return new CreatedApp(view(app), secret);
    }

    /** 按范围查询应用，排序只允许固定列。 */
    @GetMapping
    public PagedApps list(@RequestParam long systemId, @RequestParam(required = false) Long tenantId,
                          @RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long size,
                          @RequestParam(defaultValue = "createTime") String sortField,
                          @RequestParam(defaultValue = "desc") String sortOrder,
                          HttpServletRequest request) {
        authorize(request, systemId, tenantId, false);
        if (page < 1 || size < 1 || size > 100 || !List.of("createTime", "appName", "appKey").contains(sortField)
                || !List.of("asc", "desc").contains(sortOrder.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页或排序参数无效");
        }
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        LambdaQueryWrapper<UnApp> query = new LambdaQueryWrapper<UnApp>().eq(UnApp::getSystemId, systemId);
        if (tenantId == null) {
            query.isNull(UnApp::getTenantId);
        } else {
            query.eq(UnApp::getTenantId, tenantId);
        }
        switch (sortField) {
            case "appName" -> query.orderBy(true, asc, UnApp::getAppName);
            case "appKey" -> query.orderBy(true, asc, UnApp::getAppKey);
            default -> query.orderBy(true, asc, UnApp::getCreateTime);
        }
        query.orderByAsc(UnApp::getId);
        Page<UnApp> result = apps.selectPage(new Page<>(page, size), query);
        return new PagedApps(result.getRecords().stream().map(this::view).toList(), result.getTotal(), page, size);
    }

    /** 更新应用显示名称或停用状态，不允许修改生成时确定的范围和密钥。 */
    @PutMapping("/{appId}")
    public AppView update(@PathVariable long appId, @Valid @RequestBody AppUpdate input,
                          HttpServletRequest request) {
        UnApp app = requireApp(appId);
        authorize(request, app.getSystemId(), app.getTenantId(), true);
        app.setAppName(input.appName().trim());
        app.setStatus(input.enabled() ? (byte) 1 : (byte) 0);
        apps.updateById(app);
        return view(app);
    }

    /** 应用事件在所属应用内唯一，回调地址只接受 HTTPS。 */
    @PostMapping("/{appId}/events")
    public EventView createEvent(@PathVariable long appId, @Valid @RequestBody EventInput input,
                                 HttpServletRequest request) {
        UnApp app = requireApp(appId);
        authorize(request, app.getSystemId(), app.getTenantId(), true);
        validateCallback(input.callbackUrl());
        UnAppEvent event = new UnAppEvent();
        event.setAppId(appId);
        event.setEventKey(input.eventKey().trim());
        event.setCallbackUrl(input.callbackUrl().trim());
        event.setStatus((byte) 1);
        events.insert(event);
        return eventView(event);
    }

    /** 应用事件列表只显示当前可管理应用的配置。 */
    @GetMapping("/{appId}/events")
    public List<EventView> events(@PathVariable long appId, HttpServletRequest request) {
        UnApp app = requireApp(appId);
        authorize(request, app.getSystemId(), app.getTenantId(), false);
        return events.selectList(new LambdaQueryWrapper<UnAppEvent>()
                        .eq(UnAppEvent::getAppId, appId).orderByAsc(UnAppEvent::getId))
                .stream().map(this::eventView).toList();
    }

    @PutMapping("/{appId}/events/{eventId}")
    public EventView updateEvent(@PathVariable long appId, @PathVariable long eventId,
                                 @Valid @RequestBody EventUpdate input, HttpServletRequest request) {
        UnApp app = requireApp(appId);
        authorize(request, app.getSystemId(), app.getTenantId(), true);
        UnAppEvent event = events.selectById(eventId);
        if (event == null || !event.getAppId().equals(appId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用事件不存在");
        }
        validateCallback(input.callbackUrl());
        event.setCallbackUrl(input.callbackUrl().trim());
        event.setStatus(input.enabled() ? (byte) 1 : (byte) 0);
        events.updateById(event);
        return eventView(event);
    }

    private void authorize(HttpServletRequest request, long systemId, Long tenantId, boolean write) {
        UnSysSystem system = systems.selectById(systemId);
        if (system == null || system.getStatus() == null || system.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "业务系统不存在或已停用");
        }
        if (tenantId == null) {
            access.platformAdmin(request);
            return;
        }
        UnSysTenant tenant = tenants.selectById(tenantId);
        if (tenant == null || !tenant.getSystemId().equals(systemId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户不属于此系统");
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "租户不存在或已停用");
        }
        if (write) {
            access.tenantAdmin(request, tenantId);
        } else {
            access.tenantMember(request, tenantId);
        }
    }

    private UnApp requireApp(long id) {
        UnApp app = apps.selectById(id);
        if (app == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在");
        }
        return app;
    }

    private void validateCallback(String callback) {
        try {
            URI uri = new URI(callback.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getPort() > 65535) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回调地址必须为合法的 HTTPS 地址");
            }
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回调地址必须为合法的 HTTPS 地址");
        }
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ResponseStatusException invalidCredential() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "应用凭据无效");
    }

    private AppView view(UnApp app) {
        return new AppView(app.getId(), app.getSystemId(), app.getTenantId(),
                app.getAppKey(), app.getAppName(), app.getStatus());
    }

    private EventView eventView(UnAppEvent event) {
        return new EventView(event.getId(), event.getEventKey(), event.getCallbackUrl(), event.getStatus());
    }

    /** 系统范围应用仅平台管理员可创建；租户应用由该租户管理员创建。 */
    public record AppInput(long systemId, Long tenantId,
                           @NotBlank @Size(max = 64) String appKey,
                           @NotBlank @Size(max = 100) String appName) { }
    public record AppUpdate(@NotBlank @Size(max = 100) String appName, boolean enabled) { }
    public record AppView(Long id, Long systemId, Long tenantId, String appKey, String appName, Byte status) { }
    public record CreatedApp(AppView app, String secret) { }
    public record VerifyInput(@NotBlank @Size(max = 64) String appKey,
                              @NotBlank @Size(max = 200) String secret) { }
    public record AppIdentity(Long id, Long systemId, Long tenantId, String appKey, String appName) { }
    public record PagedApps(List<AppView> records, long total, long page, long size) { }
    public record EventInput(@NotBlank @Size(max = 80) String eventKey,
                             @NotBlank @Size(max = 1000) String callbackUrl) { }
    public record EventUpdate(@NotBlank @Size(max = 1000) String callbackUrl, boolean enabled) { }
    public record EventView(Long id, String eventKey, String callbackUrl, Byte status) { }
}
