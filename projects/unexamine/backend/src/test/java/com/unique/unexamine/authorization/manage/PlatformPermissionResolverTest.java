package com.unique.unexamine.authorization.manage;

import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.entity.PlatformMemberRole;
import com.unique.unexamine.platform.base.entity.PlatformRole;
import com.unique.unexamine.platform.base.entity.PlatformRolePermission;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRolePermissionBaseService;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 平台成员的建系统基础能力必须与成员停用和显式拒绝保持一致。 */
class PlatformPermissionResolverTest {
    @BeforeAll
    static void registerEntityMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "platform-permission-test");
        TableInfoHelper.initTableInfo(assistant, PlatformMember.class);
        TableInfoHelper.initTableInfo(assistant, PlatformMemberRole.class);
        TableInfoHelper.initTableInfo(assistant, PlatformRole.class);
        TableInfoHelper.initTableInfo(assistant, PlatformRolePermission.class);
    }

    private final PlatformMemberBaseService members = mock(PlatformMemberBaseService.class);
    private final PlatformMemberRoleBaseService memberRoles = mock(PlatformMemberRoleBaseService.class);
    private final PlatformRolePermissionBaseService rolePermissions = mock(PlatformRolePermissionBaseService.class);
    private final PlatformRoleBaseService roles = mock(PlatformRoleBaseService.class);
    private final PlatformPermissionResolver resolver =
            new PlatformPermissionResolver(members, memberRoles, rolePermissions, roles);

    @Test
    void newActiveMemberCanCreateSystemWithoutReceivingPlatformAdministration() {
        PlatformMember member = new PlatformMember();
        member.setId(31L);
        when(members.selectList(any())).thenReturn(List.of(member));
        when(memberRoles.selectList(any())).thenReturn(List.of());

        ResolvedPermissions result = resolver.resolve(1L, 2L);

        assertThat(result.roleIds()).isEmpty();
        assertThat(result.permissions()).containsExactly(
                new PermissionGrant("PLATFORM", "SYSTEM", "CREATE", List.of()));
    }

    @Test
    void disabledOrAbsentMemberCannotCreateSystem() {
        when(members.selectList(any())).thenReturn(List.of());

        assertThat(resolver.resolve(1L, 2L).permissions()).isEmpty();
    }

    @Test
    void explicitRoleDenialSuppressesMemberCreateGrant() {
        PlatformMember member = new PlatformMember();
        member.setId(31L);
        PlatformMemberRole membership = new PlatformMemberRole();
        membership.setRoleId(41L);
        PlatformRole role = new PlatformRole();
        role.setId(41L);
        PlatformRolePermission denied = new PlatformRolePermission();
        denied.setRoleId(41L);
        denied.setResourceType("PLATFORM");
        denied.setResourceCode("SYSTEM");
        denied.setActionCode("CREATE");
        denied.setEffect("DENY");
        when(members.selectList(any())).thenReturn(List.of(member));
        when(memberRoles.selectList(any())).thenReturn(List.of(membership));
        when(roles.selectList(any())).thenReturn(List.of(role));
        when(rolePermissions.selectList(any())).thenReturn(List.of(denied));

        assertThat(resolver.resolve(1L, 2L).permissions()).isEmpty();
    }
}
