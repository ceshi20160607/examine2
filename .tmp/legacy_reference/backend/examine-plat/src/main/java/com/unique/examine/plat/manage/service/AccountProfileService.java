package com.unique.examine.plat.manage.service;

import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.api.AuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AccountProfileService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,189}$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9][0-9 ()-]{5,31}$");

    private final PlatAccountMapper accounts;
    private final AuditFacade audit;

    public AccountProfileService(PlatAccountMapper accounts, AuditFacade audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    public ProfileView get(AuthenticatedSession session) {
        var account = requireAccount(session.accountId());
        return view(account);
    }

    @Transactional
    public ProfileView update(AuthenticatedSession session, UpdateCommand command, ClientRequest request) {
        if (command == null || command.version() == null) {
            throw validation("version is required");
        }
        var account = requireAccount(session.accountId());
        if (!account.getVersion().equals(command.version())) {
            throw new BusinessException("ACCOUNT_PROFILE_CONFLICT", "个人资料已被更新，请刷新后重试", HttpStatus.CONFLICT);
        }
        var displayName = required(command.displayName(), "displayName", 80);
        var email = optional(command.email(), 254);
        if (email != null && !EMAIL.matcher(email).matches()) throw validation("email is invalid");
        var phone = optional(command.phone(), 32);
        if (phone != null && !PHONE.matcher(phone).matches()) throw validation("phone is invalid");
        var locale = optional(command.locale(), 32);
        if (locale != null && Locale.forLanguageTag(locale).getLanguage().isBlank()) throw validation("locale is invalid");
        var timeZone = optional(command.timeZone(), 64);
        if (timeZone != null) {
            try { ZoneId.of(timeZone); } catch (RuntimeException exception) { throw validation("timeZone is invalid"); }
        }

        account.setDisplayName(displayName);
        account.setEmail(email);
        account.setEmailNormalized(email == null ? null : email.toLowerCase(Locale.ROOT));
        account.setPhone(phone);
        account.setLocale(locale);
        account.setTimeZone(timeZone);
        account.setUpdatedAt(LocalDateTime.now());
        account.setUpdatedBy(session.accountId());
        try {
            if (accounts.updateById(account) != 1) {
                throw new BusinessException("ACCOUNT_PROFILE_CONFLICT", "个人资料已被更新，请刷新后重试", HttpStatus.CONFLICT);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("ACCOUNT_EMAIL_CONFLICT", "该邮箱已被其他账号使用", HttpStatus.CONFLICT);
        }
        audit.recordSecurity(new AuditEvent("ACCOUNT_PROFILE_UPDATE", session.accountId(), null,
                session.systemId(), session.tenantId(), "WEB", request.remoteAddress(), request.userAgent(),
                request.requestId(), request.traceId(), "SUCCESS", null, "{}"));
        return view(requireAccount(session.accountId()));
    }

    private com.unique.examine.plat.base.entity.Account requireAccount(long accountId) {
        var account = accounts.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException("ACCOUNT_UNAVAILABLE", "账号当前不可用", HttpStatus.UNAUTHORIZED);
        }
        return account;
    }

    private static ProfileView view(com.unique.examine.plat.base.entity.Account account) {
        return new ProfileView(account.getId().toString(), account.getUsername(), account.getDisplayName(),
                account.getEmail(), account.getPhone(), account.getLocale(), account.getTimeZone(),
                account.getVersion());
    }

    private static String required(String value, String field, int max) {
        var result = optional(value, max);
        if (result == null) throw validation(field + " is required");
        return result;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        var result = value.trim();
        if (result.length() > max) throw validation("field is too long");
        return result;
    }

    private static BusinessException validation(String message) {
        return new BusinessException("ACCOUNT_PROFILE_INVALID", message, HttpStatus.BAD_REQUEST);
    }

    public record ProfileView(String id, String username, String displayName, String email, String phone,
                              String locale, String timeZone, Long version) { }

    public record UpdateCommand(String displayName, String email, String phone, String locale,
                                String timeZone, Long version) { }
}
