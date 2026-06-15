package com.unique.examine.plat.manage.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.enums.EnableStatus;
import com.unique.examine.plat.manage.enums.PlatErrorCode;
import com.unique.examine.plat.manage.service.PlatformModuleInitializationWriter;
import com.unique.examine.plat.manage.vo.InitializedObjectVO;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Service;

/**
 * 基于 MyBatis 连接的系统默认数据初始化写入器。
 */
@Service
@RequiredArgsConstructor
public class MybatisPlatformModuleInitializationWriter implements PlatformModuleInitializationWriter {

    private static final long ACTIVE_DELETE_TOKEN = 0L;

    private static final long SYSTEM_LEVEL_TENANT_ID = 0L;

    private static final String SYS_SUPER_ADMIN = "SYS_SUPER_ADMIN";

    private static final String DEFAULT_APP = "default_app";

    private final SqlSessionTemplate sqlSessionTemplate;

    /**
     * 初始化系统内默认成员、角色、权限目录和默认应用。
     *
     * @param command 初始化命令
     * @return 初始化结果
     */
    @Override
    public InitializationResult initialize(InitializationCommand command) {
        try {
            Connection connection = sqlSessionTemplate.getConnection();
            return doInitialize(connection, command);
        } catch (SQLException ex) {
            throw new BusinessException(PlatErrorCode.SYSTEM_INIT_FAILED, "系统默认数据初始化失败");
        }
    }

    private InitializationResult doInitialize(Connection connection, InitializationCommand command)
            throws SQLException {
        LocalDateTime now = command.now();
        List<InitializedObjectVO> initializedObjects = new ArrayList<>();

        Long memberId = IdWorker.getId();
        insertMember(connection, command, memberId, now);
        initializedObjects.add(initialized("MEMBER", "owner", memberId, EnableStatus.ENABLED.getCode()));

        Long memberTenantId = IdWorker.getId();
        insertMemberTenant(connection, command, memberTenantId, memberId, now);
        initializedObjects.add(initialized("MEMBER_TENANT", "default", memberTenantId, EnableStatus.ENABLED.getCode()));

        Long roleId = IdWorker.getId();
        insertRole(connection, command.systemId(), roleId, now);
        initializedObjects.add(initialized("ROLE", SYS_SUPER_ADMIN, roleId, EnableStatus.ENABLED.getCode()));

        Long memberRoleId = IdWorker.getId();
        insertMemberRole(connection, command.systemId(), memberRoleId, memberId, roleId, now);
        initializedObjects.add(initialized("MEMBER_ROLE", SYS_SUPER_ADMIN, memberRoleId, EnableStatus.ENABLED.getCode()));

        Long menuId = IdWorker.getId();
        insertSystemMenu(connection, command.systemId(), menuId, now);
        initializedObjects.add(initialized("SYSTEM_MENU", "SYS_MANAGE", menuId, EnableStatus.ENABLED.getCode()));

        Long operationId = IdWorker.getId();
        insertSystemOperation(connection, command.systemId(), menuId, operationId, now);
        initializedObjects.add(initialized("SYSTEM_OPERATION", "SYS_MANAGE_ALL", operationId,
                EnableStatus.ENABLED.getCode()));

        Long roleMenuId = IdWorker.getId();
        insertRoleMenu(connection, command.systemId(), roleMenuId, roleId, menuId, now);
        initializedObjects.add(initialized("ROLE_MENU", "SYS_MANAGE", roleMenuId, EnableStatus.ENABLED.getCode()));

        Long roleOperationId = IdWorker.getId();
        insertRoleOperation(connection, command.systemId(), roleOperationId, roleId, operationId, now);
        initializedObjects.add(initialized("ROLE_OPERATION", "SYS_MANAGE_ALL", roleOperationId,
                EnableStatus.ENABLED.getCode()));

        Long appId = IdWorker.getId();
        insertDefaultApp(connection, command, appId, memberId, now);
        initializedObjects.add(initialized("APP", DEFAULT_APP, appId, "DRAFT"));

        Long permissionVersionId = IdWorker.getId();
        insertPermissionVersion(connection, command.systemId(), permissionVersionId, now);
        initializedObjects.add(initialized("PERMISSION_VERSION", "INIT", permissionVersionId,
                EnableStatus.ENABLED.getCode()));

        return new InitializationResult(memberId, List.copyOf(initializedObjects));
    }

    private void insertMember(Connection connection, InitializationCommand command, Long memberId, LocalDateTime now)
            throws SQLException {
        String sql = """
                INSERT INTO un_module_member
                (id, system_id, account_id, member_code, display_name_snapshot, default_tenant_id, post_name,
                 status, super_admin_flag, last_enter_at, delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberId);
            statement.setLong(2, command.systemId());
            statement.setLong(3, command.accountId());
            statement.setString(4, "owner_" + command.accountId());
            statement.setString(5, command.displayName());
            statement.setLong(6, command.tenantId());
            statement.setString(7, "系统创建人");
            statement.setString(8, EnableStatus.ENABLED.getCode());
            statement.setByte(9, (byte) 1);
            statement.setNull(10, Types.TIMESTAMP);
            statement.setLong(11, ACTIVE_DELETE_TOKEN);
            statement.setObject(12, now);
            statement.setObject(13, now);
            statement.executeUpdate();
        }
    }

    private void insertMemberTenant(Connection connection, InitializationCommand command, Long memberTenantId,
            Long memberId, LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO un_module_member_tenant
                (id, system_id, member_id, tenant_id, primary_flag, delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberTenantId);
            statement.setLong(2, command.systemId());
            statement.setLong(3, memberId);
            statement.setLong(4, command.tenantId());
            statement.setByte(5, (byte) 1);
            statement.setLong(6, ACTIVE_DELETE_TOKEN);
            statement.setObject(7, now);
            statement.setObject(8, now);
            statement.executeUpdate();
        }
    }

    private void insertRole(Connection connection, Long systemId, Long roleId, LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO un_module_role
                (id, system_id, tenant_id, code, name, description, status, protected_flag, sort_order,
                 delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, roleId);
            statement.setLong(2, systemId);
            statement.setLong(3, SYSTEM_LEVEL_TENANT_ID);
            statement.setString(4, SYS_SUPER_ADMIN);
            statement.setString(5, "系统超级管理员");
            statement.setString(6, "系统创建时自动初始化的系统内最高权限角色");
            statement.setString(7, EnableStatus.ENABLED.getCode());
            statement.setByte(8, (byte) 1);
            statement.setInt(9, 1);
            statement.setLong(10, ACTIVE_DELETE_TOKEN);
            statement.setObject(11, now);
            statement.setObject(12, now);
            statement.executeUpdate();
        }
    }

    private void insertMemberRole(Connection connection, Long systemId, Long memberRoleId, Long memberId, Long roleId,
            LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO un_module_member_role
                (id, system_id, member_id, role_id, delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memberRoleId);
            statement.setLong(2, systemId);
            statement.setLong(3, memberId);
            statement.setLong(4, roleId);
            statement.setLong(5, ACTIVE_DELETE_TOKEN);
            statement.setObject(6, now);
            statement.setObject(7, now);
            statement.executeUpdate();
        }
    }

    private void insertSystemMenu(Connection connection, Long systemId, Long menuId, LocalDateTime now)
            throws SQLException {
        String sql = """
                INSERT INTO un_module_system_menu
                (id, system_id, tenant_id, parent_id, code, name, menu_type, source_type, source_id, path, status,
                 sort_order, depth_level, depth_path, delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, menuId);
            statement.setLong(2, systemId);
            statement.setLong(3, SYSTEM_LEVEL_TENANT_ID);
            statement.setLong(4, 0L);
            statement.setString(5, "SYS_MANAGE");
            statement.setString(6, "系统管理");
            statement.setString(7, "ADMIN");
            statement.setString(8, "SYSTEM");
            statement.setLong(9, systemId);
            statement.setString(10, "/systems/" + systemId + "/manage");
            statement.setString(11, EnableStatus.ENABLED.getCode());
            statement.setInt(12, 1);
            statement.setInt(13, 1);
            statement.setString(14, "/" + menuId + "/");
            statement.setLong(15, ACTIVE_DELETE_TOKEN);
            statement.setObject(16, now);
            statement.setObject(17, now);
            statement.executeUpdate();
        }
    }

    private void insertSystemOperation(Connection connection, Long systemId, Long menuId, Long operationId,
            LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO un_module_system_operation
                (id, system_id, menu_id, code, name, operation_type, resource_type, resource_id, api_pattern,
                 method, status, delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, operationId);
            statement.setLong(2, systemId);
            statement.setLong(3, menuId);
            statement.setString(4, "SYS_MANAGE_ALL");
            statement.setString(5, "系统管理全部操作");
            statement.setString(6, "API");
            statement.setString(7, "SYSTEM");
            statement.setLong(8, systemId);
            statement.setString(9, "/api/v1/systems/" + systemId + "/**");
            statement.setString(10, "*");
            statement.setString(11, EnableStatus.ENABLED.getCode());
            statement.setLong(12, ACTIVE_DELETE_TOKEN);
            statement.setObject(13, now);
            statement.setObject(14, now);
            statement.executeUpdate();
        }
    }

    private void insertRoleMenu(Connection connection, Long systemId, Long roleMenuId, Long roleId, Long menuId,
            LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO un_module_role_menu
                (id, system_id, role_id, menu_id, delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, roleMenuId);
            statement.setLong(2, systemId);
            statement.setLong(3, roleId);
            statement.setLong(4, menuId);
            statement.setLong(5, ACTIVE_DELETE_TOKEN);
            statement.setObject(6, now);
            statement.setObject(7, now);
            statement.executeUpdate();
        }
    }

    private void insertRoleOperation(Connection connection, Long systemId, Long roleOperationId, Long roleId,
            Long operationId, LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO un_module_role_operation
                (id, system_id, role_id, operation_id, operation_code, delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, roleOperationId);
            statement.setLong(2, systemId);
            statement.setLong(3, roleId);
            statement.setLong(4, operationId);
            statement.setString(5, "SYS_MANAGE_ALL");
            statement.setLong(6, ACTIVE_DELETE_TOKEN);
            statement.setObject(7, now);
            statement.setObject(8, now);
            statement.executeUpdate();
        }
    }

    private void insertDefaultApp(Connection connection, InitializationCommand command, Long appId, Long memberId,
            LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO un_module_app
                (app_id, system_id, tenant_id, name, code, icon, description, app_status, current_app_version_id,
                 module_count, sort_order, version, delete_marker, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, appId);
            statement.setLong(2, command.systemId());
            statement.setLong(3, command.tenantId());
            statement.setString(4, "默认应用");
            statement.setString(5, DEFAULT_APP);
            statement.setString(6, "app");
            statement.setString(7, "系统创建时自动初始化的默认应用");
            statement.setString(8, "DRAFT");
            statement.setNull(9, Types.BIGINT);
            statement.setInt(10, 0);
            statement.setInt(11, 1);
            statement.setInt(12, 1);
            statement.setString(13, "0");
            statement.setLong(14, memberId);
            statement.setLong(15, memberId);
            statement.setObject(16, now);
            statement.setObject(17, now);
            statement.executeUpdate();
        }
    }

    private void insertPermissionVersion(Connection connection, Long systemId, Long permissionVersionId,
            LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO un_module_permission_version
                (id, system_id, tenant_id, version_no, changed_reason, changed_at, delete_token, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, permissionVersionId);
            statement.setLong(2, systemId);
            statement.setLong(3, SYSTEM_LEVEL_TENANT_ID);
            statement.setLong(4, 1L);
            statement.setString(5, "SYSTEM_INIT");
            statement.setObject(6, now);
            statement.setLong(7, ACTIVE_DELETE_TOKEN);
            statement.setObject(8, now);
            statement.setObject(9, now);
            statement.executeUpdate();
        }
    }

    private InitializedObjectVO initialized(String objectType, String code, Long id, String status) {
        return InitializedObjectVO.builder()
                .objectType(objectType)
                .code(code)
                .id(String.valueOf(id))
                .status(status)
                .build();
    }
}
