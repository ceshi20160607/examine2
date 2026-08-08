package com.unique.examine.plat.manage.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCatalogTest {
    @Test
    void includesPersonalPlatformTaskPermissionsForNewPlatforms() {
        assertThat(PermissionCatalog.PLATFORM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("platform.task.read", "platform.task.create",
                        "platform.task.manage");
        assertThat(PermissionCatalog.require("platform.task.read").resourceType())
                .isEqualTo("DATA");
        assertThat(PermissionCatalog.require("platform.task.create").resourceType())
                .isEqualTo("ACTION");
        assertThat(PermissionCatalog.require("platform.task.manage").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesPlatformOpenApiManagementForNewPlatforms() {
        assertThat(PermissionCatalog.PLATFORM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("platform.openapi.application.manage");
        assertThat(PermissionCatalog.require("platform.openapi.application.manage")
                .resourceType()).isEqualTo("ACTION");
    }

    @Test
    void includesPlatformFlowAndDashboardPermissionsForNewPlatforms() {
        assertThat(PermissionCatalog.PLATFORM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("platform.flow.read", "platform.flow.start",
                        "platform.flow.manage", "platform.dashboard.view",
                        "platform.dashboard.manage");
        assertThat(PermissionCatalog.require("platform.dashboard.view").resourceType())
                .isEqualTo("MENU");
        assertThat(PermissionCatalog.require("platform.flow.manage").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesAiAgentPermissionsForNewSystems() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("ai.agent.use", "ai.policy.manage");
        assertThat(PermissionCatalog.require("ai.agent.use").resourceType())
                .isEqualTo("MENU");
        assertThat(PermissionCatalog.require("ai.policy.manage").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesModuleConfigurationAuthorityForNewSystems() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("module.config.manage");
        assertThat(PermissionCatalog.require("module.config.manage")
                .resourceType()).isEqualTo("ACTION");
    }

    @Test
    void includesDailyWorkReportPermissionsForNewSystems() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains(
                        "work.report.access",
                        "work.report.create",
                        "work.report.manage");
        assertThat(PermissionCatalog.require("work.report.access")
                .resourceType()).isEqualTo("MENU");
        assertThat(PermissionCatalog.require("work.report.create")
                .resourceType()).isEqualTo("ACTION");
        assertThat(PermissionCatalog.require("work.report.manage")
                .resourceType()).isEqualTo("DATA");
    }

    @Test
    void includesWorkProjectPermissionsForSystemsCreatedAfterTheMigrationChain() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains(
                        "work.project.access",
                        "work.project.create",
                        "work.project.manage"
                );
        assertThat(PermissionCatalog.require("work.project.access").resourceType())
                .isEqualTo("MENU");
        assertThat(PermissionCatalog.require("work.project.create").resourceType())
                .isEqualTo("ACTION");
        assertThat(PermissionCatalog.require("work.project.manage").resourceType())
                .isEqualTo("DATA");
    }

    @Test
    void includesOpenApiApplicationManagementForNewSystems() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("openapi.application.manage");
        assertThat(PermissionCatalog.require("openapi.application.manage").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesRequesterWithdrawalForSystemsCreatedAfterTheMigrationChain() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("flow.instance.withdraw");
        assertThat(PermissionCatalog.require("flow.instance.withdraw").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesFlowTerminationForSystemsCreatedAfterTheMigrationChain() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("flow.instance.terminate");
        assertThat(PermissionCatalog.require("flow.instance.terminate").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesFlowUrgeAndCommentForSystemsCreatedAfterTheMigrationChain() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("flow.instance.urge", "flow.instance.comment");
        assertThat(PermissionCatalog.require("flow.instance.urge").resourceType())
                .isEqualTo("ACTION");
        assertThat(PermissionCatalog.require("flow.instance.comment").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesFlowTransferAndAddSignForSystemsCreatedAfterTheMigrationChain() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("flow.instance.transfer", "flow.instance.add-sign");
        assertThat(PermissionCatalog.require("flow.instance.transfer").resourceType())
                .isEqualTo("ACTION");
        assertThat(PermissionCatalog.require("flow.instance.add-sign").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesFlowReturnAndClaimPermissionsForSystemsCreatedAfterTheMigrationChain() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains(
                        "flow.instance.return",
                        "flow.instance.claim",
                        "flow.instance.cancel-claim"
                );
        assertThat(PermissionCatalog.require("flow.instance.return").resourceType())
                .isEqualTo("ACTION");
        assertThat(PermissionCatalog.require("flow.instance.claim").resourceType())
                .isEqualTo("ACTION");
        assertThat(PermissionCatalog.require("flow.instance.cancel-claim").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesFlowReduceSignAndCopyForSystemsCreatedAfterTheMigrationChain() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("flow.instance.reduce-sign", "flow.instance.copy");
        assertThat(PermissionCatalog.require("flow.instance.reduce-sign").resourceType())
                .isEqualTo("ACTION");
        assertThat(PermissionCatalog.require("flow.instance.copy").resourceType())
                .isEqualTo("ACTION");
    }

    @Test
    void includesExternalTaskWorkerForSystemsCreatedAfterTheMigrationChain() {
        assertThat(PermissionCatalog.SYSTEM)
                .extracting(PermissionCatalog.Definition::code)
                .contains("flow.external-task.work");
        assertThat(PermissionCatalog.require(
                "flow.external-task.work").resourceType())
                .isEqualTo("ACTION");
    }
}
