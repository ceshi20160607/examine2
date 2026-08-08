package com.unique.examine.module.dashboard.service;

import com.unique.examine.module.dashboard.domain.DashboardActor;
import com.unique.examine.module.dashboard.domain.DashboardDraft;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardScopeBinding;
import com.unique.examine.module.dashboard.domain.PublishedDashboard;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.dashboard.port.DashboardScopeBindingStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class DashboardScopeService {
    private final DashboardService dashboards;
    private final DashboardScopeBindingStore bindings;

    public DashboardScopeService(
            DashboardService dashboards,
            DashboardScopeBindingStore bindings
    ) {
        this.dashboards = Objects.requireNonNull(dashboards, "dashboards");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    @Transactional
    public SystemDashboard create(
            DashboardActor actor,
            DashboardPlacement placement,
            String scopeKey,
            String code,
            String name,
            String description
    ) {
        requireScoped(placement);
        var owner = placement == DashboardPlacement.PERSONAL_HOME
                ? actor.memberId() : 0L;
        if (bindings.find(actor.systemId(), actor.tenantId(), placement,
                scopeKey, owner).isPresent()) {
            throw new DashboardException(
                    "DASHBOARD_SCOPE_CONFLICT",
                    "A dashboard already exists for this scope");
        }
        var root = dashboards.create(actor, code, placement, name,
                description, DashboardDraft.empty());
        bindings.bind(new DashboardScopeBinding(
                root.id(), actor.systemId(), actor.tenantId(), placement,
                scopeKey, owner));
        return root;
    }

    public SystemDashboard detail(
            DashboardActor actor,
            DashboardPlacement placement,
            String scopeKey
    ) {
        var binding = binding(actor, placement, scopeKey);
        return requirePlacement(
                dashboards.detail(actor, binding.dashboardId()), placement);
    }

    public PublishedDashboard active(
            DashboardActor actor,
            DashboardPlacement placement,
            String scopeKey
    ) {
        var binding = binding(actor, placement, scopeKey);
        var published = dashboards.active(actor, binding.dashboardId());
        requirePlacement(published.root(), placement);
        return published;
    }

    public SystemDashboard owned(DashboardActor actor, long dashboardId) {
        return ownedScoped(actor, dashboardId).dashboard();
    }

    public ScopedDashboard ownedScoped(
            DashboardActor actor,
            long dashboardId
    ) {
        var binding = bindings.findByDashboard(
                        actor.systemId(), actor.tenantId(), dashboardId)
                .orElseThrow(DashboardScopeService::notFound);
        if (binding.placement() != DashboardPlacement.PERSONAL_HOME
                || binding.ownerMemberId() != actor.memberId()) {
            throw notFound();
        }
        return scoped(binding, dashboards.detail(actor, dashboardId),
                DashboardPlacement.PERSONAL_HOME);
    }

    public List<SystemDashboard> personal(DashboardActor actor) {
        return personalDashboards(actor).stream()
                .map(ScopedDashboard::dashboard).toList();
    }

    public List<ScopedDashboard> personalDashboards(DashboardActor actor) {
        return bindings.findPersonal(
                        actor.systemId(), actor.tenantId(), actor.memberId())
                .stream().map(binding -> scoped(binding, dashboards.detail(
                        actor, binding.dashboardId()),
                        DashboardPlacement.PERSONAL_HOME)).toList();
    }

    public List<ScopedDashboard> publicDashboards(
            DashboardActor actor,
            DashboardPlacement placement
    ) {
        if (placement != null) {
            requirePublic(placement);
        }
        return bindings.findPublic(actor.systemId(), actor.tenantId()).stream()
                .filter(binding -> placement == null
                        || binding.placement() == placement)
                .map(binding -> scoped(binding, dashboards.detail(
                        actor, binding.dashboardId()), binding.placement()))
                .toList();
    }

    private DashboardScopeBinding binding(
            DashboardActor actor,
            DashboardPlacement placement,
            String scopeKey
    ) {
        requireScoped(placement);
        var owner = placement == DashboardPlacement.PERSONAL_HOME
                ? actor.memberId() : 0L;
        return bindings.find(actor.systemId(), actor.tenantId(), placement,
                scopeKey, owner).orElseThrow(DashboardScopeService::notFound);
    }

    private static void requireScoped(DashboardPlacement placement) {
        if (placement == null || placement == DashboardPlacement.SYSTEM_HOME) {
            throw new DashboardException(
                    "DASHBOARD_SCOPE_INVALID",
                    "A scoped dashboard placement is required");
        }
    }

    private static void requirePublic(DashboardPlacement placement) {
        if (placement != DashboardPlacement.APPLICATION_HOME
                && placement != DashboardPlacement.MODULE_HOME) {
            throw new DashboardException(
                    "DASHBOARD_SCOPE_INVALID",
                    "A public dashboard placement is required");
        }
    }

    private static ScopedDashboard scoped(
            DashboardScopeBinding binding,
            SystemDashboard dashboard,
            DashboardPlacement placement
    ) {
        return new ScopedDashboard(binding.scopeKey(),
                requirePlacement(dashboard, placement));
    }

    private static SystemDashboard requirePlacement(
            SystemDashboard root,
            DashboardPlacement placement
    ) {
        if (root.placement() != placement) {
            throw notFound();
        }
        return root;
    }

    private static DashboardException notFound() {
        return new DashboardException(
                "DASHBOARD_NOT_FOUND", "Dashboard does not exist");
    }

    public record ScopedDashboard(
            String scopeKey,
            SystemDashboard dashboard
    ) {
        public ScopedDashboard {
            Objects.requireNonNull(scopeKey, "scopeKey");
            Objects.requireNonNull(dashboard, "dashboard");
        }
    }
}
