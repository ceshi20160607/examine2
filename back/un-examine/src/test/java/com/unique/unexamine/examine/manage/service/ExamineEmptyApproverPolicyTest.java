package com.unique.unexamine.examine.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.examine.base.entity.UnExamineExample;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatDepartmentService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberRoleService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证审批无人可办时只按实例快照解析指定兜底目标或审批管理员。 */
class ExamineEmptyApproverPolicyTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void defaultsToAdministratorSavedInDefinitionSnapshot() throws Exception {
        UnExamineExample example = example("""
                {"administratorAccountId":10,"definition":{"administratorAccountId":20}}
                """);
        var node = json.readTree("{}");
        Set<Long> result = ExamineEmptyApproverPolicy.resolve(json, example, node, Map.of(),
                List.of(activePlatformCandidates()));
        assertEquals(Set.of(20L), result);
        assertEquals("ADMIN", ExamineEmptyApproverPolicy.ruleType(node));
    }

    @Test
    void configuredFixedPersonOverridesAdministrator() throws Exception {
        UnExamineExample example = example("""
                {"administratorAccountId":10,"definition":{"administratorAccountId":20}}
                """);
        var node = json.readTree("""
                {"config":{"emptyApprover":{"type":"ACCOUNT","subjectId":30}}}
                """);
        ExamineEmptyApproverPolicy.validateNode(node, "review");
        assertEquals(Set.of(30L), ExamineEmptyApproverPolicy.resolve(json, example, node, Map.of(),
                List.of(activePlatformCandidates())));
    }

    @Test
    void rejectsRuleWithoutRequiredTarget() throws Exception {
        var node = json.readTree("""
                {"emptyApprover":{"type":"ROLE"}}
                """);
        assertThrows(BaseException.class, () -> ExamineEmptyApproverPolicy.validateNode(node, "review"));
    }

    @Test
    void configuredRoleResolvesActivePlatformMember() throws Exception {
        var node = json.readTree("""
                {"emptyApprover":{"type":"ROLE","subjectId":7}}
                """);
        IUnPlatAccountService accounts = mock(IUnPlatAccountService.class);
        IUnPlatMemberService members = mock(IUnPlatMemberService.class);
        IUnPlatMemberRoleService memberRoles = mock(IUnPlatMemberRoleService.class);
        IUnPlatRoleService roles = mock(IUnPlatRoleService.class);
        UnPlatRole role = new UnPlatRole(); role.setStatus("ACTIVE");
        when(roles.queryById(7L)).thenReturn(role);
        UnPlatMemberRole link = new UnPlatMemberRole(); link.setMemberId(4L);
        when(memberRoles.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(link));
        UnPlatMember member = new UnPlatMember(); member.setAccountId(9L); member.setStatus("ACTIVE");
        when(members.queryById(4L)).thenReturn(member);
        UnPlatAccount account = new UnPlatAccount(); account.setStatus("ACTIVE");
        when(accounts.queryById(9L)).thenReturn(account);
        var resolver = new PlatformExamineCandidateResolver(accounts, members, memberRoles, roles,
                mock(IUnPlatDepartmentService.class));
        assertEquals(Set.of(9L), ExamineEmptyApproverPolicy.resolve(json,
                example("{\"administratorAccountId\":10}"), node, Map.of(), List.of(resolver)));
    }

    private UnExamineExample example(String runtime) {
        var example = new UnExamineExample();
        example.setRuntimeSnapshotJson(runtime);
        return example;
    }

    private SnapshotExamineCandidateResolver activePlatformCandidates() {
        IUnPlatAccountService accounts = mock(IUnPlatAccountService.class);
        when(accounts.queryById(anyLong())).thenAnswer(call -> {
            UnPlatAccount account = new UnPlatAccount();
            account.setStatus("ACTIVE");
            return account;
        });
        return new SnapshotExamineCandidateResolver(accounts);
    }
}
