package com.unique.examine.module.dashboard.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerScopeBoundaryTest {
    @Test
    void genericAdminEndpointsAreSystemOnlyAndScopedMutationsResolveBinding()
            throws Exception {
        var admin = normalize(Files.readString(source(
                "DashboardAdminController.java")));
        var scoped = normalize(Files.readString(source(
                "DashboardScopeController.java")));

        assertThat(admin)
                .contains("root.placement() == dashboardplacement.system_home")
                .contains("if (placement != dashboardplacement.system_home)")
                .contains("system(actor, dashboardid)")
                .contains("dashboard_scope_required");
        assertThat(scoped)
                .contains("@getmapping(\"/admin/dashboard-scopes\")")
                .contains("@requestparam(required = false) string placement")
                .contains("scopes.publicdashboards(")
                .contains("entry.dashboard(), entry.scopekey()")
                .contains("scopes.detail( actor, publicplacement(placement), scopekey)")
                .contains("scopes.owned(actor, dashboardid)")
                .contains("memberactor(runtimesession.require(value, systemid))")
                .contains("adminactor(value, systemid)")
                .contains("placement != dashboardplacement.application_home")
                .contains("placement != dashboardplacement.module_home")
                .doesNotContain("dashboards.detail(actor, dashboardid)");
    }

    private static Path source(String name) throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "src/main/java/com/unique/examine/module/dashboard/controller/"
                            + name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate dashboard controller " + name);
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
