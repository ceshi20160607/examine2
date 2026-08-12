package com.unique.examine.plat.vnext.manage.systementry;

import com.unique.examine.core.context.RequestSession;
import com.unique.examine.plat.vnext.manage.auth.IssuedSession;
import com.unique.examine.plat.vnext.manage.auth.SystemSummary;
import org.springframework.stereotype.Service;

import java.util.List;

/** Public use-case facade; authentication input is always the server-side request attribute. */
@Service
public class VNextSystemEntryService {
    private final VNextAuthorizedSystemService authorizedSystemService;
    private final VNextSystemSwitchService switchService;

    public VNextSystemEntryService(
            VNextAuthorizedSystemService authorizedSystemService,
            VNextSystemSwitchService switchService
    ) {
        this.authorizedSystemService = authorizedSystemService;
        this.switchService = switchService;
    }

    public List<SystemSummary> list(Object authenticated) {
        return authorizedSystemService.list(require(authenticated).accountId());
    }

    public IssuedSession switchSystem(Object authenticated, String rawSystemId) {
        var session = require(authenticated);
        return switchService.switchSystem(session, parseSystemId(rawSystemId));
    }

    private static RequestSession require(Object value) {
        if (!(value instanceof RequestSession session)) throw SystemEntryErrors.sessionRequired();
        return session;
    }

    private static long parseSystemId(String raw) {
        try {
            var parsed = Long.parseLong(raw);
            return parsed > 0 ? parsed : -1L;
        } catch (RuntimeException exception) {
            return -1L;
        }
    }
}
