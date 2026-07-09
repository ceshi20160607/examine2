package com.unique.unexamine.service;

import com.unique.unexamine.core.auth.LoginRequest;
import com.unique.unexamine.core.auth.LoginResponse;
import com.unique.unexamine.core.auth.SwitchContextRequest;
import com.unique.unexamine.core.auth.SystemOption;
import com.unique.unexamine.core.auth.UserContext;
import com.unique.unexamine.core.auth.UserProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final Map<String, SeedAccount> accounts = Map.of(
            "admin", new SeedAccount(
                    new UserProfile("acc-admin", "admin", "平台管理员", List.of("PLATFORM_ROOT")),
                    "123123aa",
                    List.of(
                            new SystemOption("sys-sales", "销售协作系统", "bind-admin-sales", List.of("SYSTEM_SUPER_ADMIN")),
                            new SystemOption("sys-quality", "质量管理系统", "bind-admin-quality", List.of("SYSTEM_ADMIN"))
                    )
            ),
            "che", new SeedAccount(
                    new UserProfile("acc-che", "che", "系统成员", List.of("PLATFORM_MEMBER")),
                    "123123aa",
                    List.of(
                            new SystemOption("sys-sales", "销售协作系统", "bind-che-sales", List.of("SYSTEM_MEMBER"))
                    )
            )
    );

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public LoginResponse login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入账号和密码");
        }
        SeedAccount account = accounts.get(request.username());
        if (account == null || !account.password().equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码不正确");
        }
        String token = UUID.randomUUID().toString();
        UserContext context = platformContext(account);
        sessions.put(token, new SessionState(account, context));
        return new LoginResponse(token, account.profile(), context, account.systems());
    }

    public LoginResponse profile(String authorization) {
        SessionState session = requireSession(authorization);
        return new LoginResponse(null, session.account().profile(), session.context(), session.account().systems());
    }

    public UserContext switchContext(String authorization, SwitchContextRequest request) {
        SessionState session = requireSession(authorization);
        String target = request == null || request.target() == null ? "platform" : request.target();
        UserContext context;
        if ("system".equalsIgnoreCase(target)) {
            String systemId = request == null ? null : request.systemId();
            SystemOption option = session.account().systems().stream()
                    .filter(system -> system.systemId().equals(systemId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "没有该系统成员上下文"));
            context = new UserContext(
                    "system",
                    option.systemId(),
                    option.systemName(),
                    option.accountMemberBindingId(),
                    option.effectiveRoles()
            );
        } else {
            context = platformContext(session.account());
        }
        sessions.put(bearerToken(authorization), new SessionState(session.account(), context));
        return context;
    }

    public UserContext currentContextOrDefault(String authorization, String requestedContext) {
        if (authorization != null && !authorization.isBlank()) {
            return requireSession(authorization).context();
        }
        if ("system".equalsIgnoreCase(requestedContext)) {
            return new UserContext("system", "sys-sales", "销售协作系统", "demo-binding", List.of("SYSTEM_ADMIN"));
        }
        return new UserContext("platform", null, "平台", null, List.of("PLATFORM_ROOT"));
    }

    public void logout(String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            sessions.remove(bearerToken(authorization));
        }
    }

    private SessionState requireSession(String authorization) {
        String token = bearerToken(authorization);
        SessionState session = sessions.get(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return session;
    }

    private String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少登录凭证");
        }
        return authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    }

    private UserContext platformContext(SeedAccount account) {
        return new UserContext("platform", null, "平台", null, account.profile().platformRoles());
    }

    private record SeedAccount(UserProfile profile, String password, List<SystemOption> systems) {
    }

    private record SessionState(SeedAccount account, UserContext context) {
    }
}