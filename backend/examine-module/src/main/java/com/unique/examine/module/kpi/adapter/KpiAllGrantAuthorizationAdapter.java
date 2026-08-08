package com.unique.examine.module.kpi.adapter;

import com.unique.examine.core.api.RuntimeAuthorizationFacade;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiException;
import com.unique.examine.module.kpi.port.KpiStatisticsExecutor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Narrow fail-closed adapter for KPI calculation authority. It rejects any
 * source grant that is denied, scoped below ALL, or resolved at a stale epoch.
 */
@Component
public final class KpiAllGrantAuthorizationAdapter {
    private final RuntimeAuthorizationFacade authorization;

    public KpiAllGrantAuthorizationAdapter(
            RuntimeAuthorizationFacade authorization
    ) {
        this.authorization = Objects.requireNonNull(
                authorization, "authorization");
    }

    public KpiStatisticsExecutor.Authorization require(
            KpiActor actor,
            String moduleCode
    ) {
        Objects.requireNonNull(actor, "actor");
        var permission = modulePermission(moduleCode);
        var grant = resolve(actor, permission);
        if (grant.denied() || !grant.allRecords()) {
            throw forbidden();
        }
        requireCurrentEpoch(actor.systemId(), grant.authzEpoch());
        return new KpiStatisticsExecutor.Authorization(grant.authzEpoch());
    }

    public boolean permissionAtEpoch(
            KpiActor actor,
            String permissionCode,
            long expectedEpoch
    ) {
        var grant = resolve(actor, permissionCode);
        if (grant.authzEpoch() != expectedEpoch) {
            throw changed();
        }
        return !grant.denied();
    }

    public void requireStillAll(
            KpiActor actor,
            String moduleCode,
            long expectedEpoch
    ) {
        var current = require(actor, moduleCode);
        if (current.epoch() != expectedEpoch) {
            throw changed();
        }
    }

    private RuntimeAuthorizationFacade.RuntimeGrant resolve(
            KpiActor actor,
            String permissionCode
    ) {
        return authorization.resolve(
                new RuntimeAuthorizationFacade.RuntimeAuthorizationRequest(
                        actor.systemId(), actor.tenantId(), actor.memberId(),
                        permissionCode));
    }

    private void requireCurrentEpoch(long systemId, long expectedEpoch) {
        if (expectedEpoch <= 0
                || authorization.currentSystemEpoch(systemId)
                != expectedEpoch) {
            throw changed();
        }
    }

    private static String modulePermission(String moduleCode) {
        if (moduleCode == null
                || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new KpiException(
                    "KPI_SOURCE_INVALID", "KPI source module code is invalid");
        }
        return "module." + moduleCode + ".view";
    }

    private static KpiException forbidden() {
        return new KpiException(
                "KPI_CALCULATION_FORBIDDEN",
                "KPI calculation requires an ALL record grant");
    }

    private static KpiException changed() {
        return new KpiException(
                "KPI_AUTHORIZATION_CONFLICT",
                "KPI calculation authorization changed; retry the command");
    }
}
