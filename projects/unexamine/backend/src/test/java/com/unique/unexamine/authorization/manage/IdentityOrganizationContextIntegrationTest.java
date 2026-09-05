package com.unique.unexamine.authorization.manage;

import com.fasterxml.jackson.databind.node.NullNode;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.platform.manage.registration.RegistrationRequest;
import com.unique.unexamine.platform.manage.registration.RegistrationResult;
import com.unique.unexamine.platform.manage.registration.RegistrationTransactionService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdentityOrganizationContextIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.bootstrap.default-admin.deployment-key", () -> "organization-context-integration-2026");
    }

    @Autowired
    private RegistrationTransactionService registrationService;
    @Autowired
    private SystemAuthorizationManagementService authorizationService;
    @Autowired
    private SystemPeopleDirectoryService directoryService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsBusinessOrganizationDirectoryPublishesRoleAndRejectsReportingCycle() {
        RegistrationResult owner = registrationService.create(new RegistrationRequest(
                "org_owner", "correct-password", "客户运营负责人", "org-owner@example.com",
                "客户运营中心", null), "organization-owner");
        registrationService.create(new RegistrationRequest(
                "org_sales", "correct-password", "销售顾问林晓", "org-sales@example.com",
                "个人试用系统", null), "organization-target");
        AuthenticatedContext context = ownerContext(owner);
        long ownerTenantMemberId = context.tenantMemberId();

        SystemAuthorizationModels.DepartmentView sales = authorizationService.saveDepartment(context,
                new SystemAuthorizationModels.DepartmentRequest(null, rootDepartment(owner.tenantId()), null,
                        "销售部", 10, null), "organization-department");
        assertThat(sales.code()).matches("department_[a-f0-9]{10}");

        SystemAuthorizationModels.RoleView roleDraft = authorizationService.saveRole(context,
                new SystemAuthorizationModels.SaveRoleRequest(null, null, "销售顾问", "维护本人负责的客户",
                        List.of(new SystemAuthorizationModels.PermissionInput(
                                "CONFIG", "SYSTEM", "MANAGE", "SELF", null)), List.of(), null),
                "organization-role-draft");
        assertThat(roleDraft.code()).matches("role_[a-f0-9]{10}");

        SystemAuthorizationModels.MemberView target = authorizationService.addMember(context,
                new SystemAuthorizationModels.AddMemberRequest("org_sales", "SALE-001", sales.id(),
                        ownerTenantMemberId, "销售顾问", List.of(roleDraft.id())), "organization-member");
        assertThat(target.displayName()).isEqualTo("销售顾问林晓");
        assertThat(target.departmentName()).isEqualTo("销售部");
        assertThat(target.managerName()).isEqualTo("客户运营负责人");
        assertThat(target.positionTitle()).isEqualTo("销售顾问");

        SystemAuthorizationModels.RoleView published = authorizationService.publishRole(context, roleDraft.id(),
                new SystemAuthorizationModels.PublishRoleRequest("销售团队开始使用", roleDraft.version()),
                "organization-role-publish");
        assertThat(published.status()).isEqualTo("ACTIVE");

        SystemPeopleDirectoryModels.Directory directory = directoryService.directory(context, "销售");
        assertThat(directory.permissionVersion()).isEqualTo(2);
        assertThat(directory.people()).singleElement().satisfies(person -> {
            assertThat(person.displayName()).isEqualTo("销售顾问林晓");
            assertThat(person.departmentName()).isEqualTo("销售部");
            assertThat(person.managerName()).isEqualTo("客户运营负责人");
            assertThat(person.roleNames()).containsExactly("销售顾问");
        });

        SystemAuthorizationModels.PermissionPreview preview = authorizationService.preview(context,
                new SystemAuthorizationModels.PreviewRequest(target.tenantMemberId(), "SYSTEM", "MANAGE", 2L),
                "organization-preview");
        assertThat(preview.actions()).singleElement().satisfies(decision -> {
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.dataScope().terms()).singleElement()
                    .satisfies(term -> assertThat(term.type()).isEqualTo("SELF"));
        });

        long superRoleId = jdbc.queryForObject(
                "select id from sys_role where tenant_id=? and code='SYSTEM_SUPER_ADMIN'", Long.class, owner.tenantId());
        int ownerVersion = jdbc.queryForObject(
                "select version from sys_tenant_member where id=?", Integer.class, ownerTenantMemberId);
        assertThatThrownBy(() -> authorizationService.assignMember(context, ownerTenantMemberId,
                new SystemAuthorizationModels.MemberAssignmentRequest(rootDepartment(owner.tenantId()),
                        target.tenantMemberId(), "客户运营负责人", List.of(superRoleId), ownerVersion),
                "organization-cycle"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("循环");
        assertThat(jdbc.queryForObject("select count(*) from sys_member_reporting_line where tenant_id=?",
                Integer.class, owner.tenantId())).isEqualTo(1);
    }

    private AuthenticatedContext ownerContext(RegistrationResult owner) {
        long accountId = jdbc.queryForObject("select id from plat_account where username='org_owner'", Long.class);
        long platformId = jdbc.queryForObject("select platform_id from plat_member where account_id=?", Long.class, accountId);
        long memberId = jdbc.queryForObject("select id from sys_member where system_id=? and account_id=?",
                Long.class, owner.systemId(), accountId);
        long tenantMemberId = jdbc.queryForObject("select id from sys_tenant_member where tenant_id=? and system_member_id=?",
                Long.class, owner.tenantId(), memberId);
        long roleId = jdbc.queryForObject("select id from sys_role where tenant_id=? and code='SYSTEM_SUPER_ADMIN'",
                Long.class, owner.tenantId());
        PermissionGrant wildcard = new PermissionGrant("*", "*", "*", List.of(roleId));
        DataScopeExpression all = new DataScopeExpression("ALL",
                List.of(new DataScopeTerm("ALL", NullNode.getInstance(), List.of(roleId))));
        return new AuthenticatedContext(null, accountId, platformId, owner.systemId(), owner.tenantId(), memberId,
                tenantMemberId, "org_owner", "客户运营负责人", "NONE", List.of(roleId), List.of(wildcard),
                Map.of("*:*:*", all));
    }

    private long rootDepartment(long tenantId) {
        return jdbc.queryForObject("select id from sys_department where tenant_id=? and parent_id is null",
                Long.class, tenantId);
    }
}
