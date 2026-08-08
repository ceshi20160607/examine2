package com.unique.examine.plat.directory;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberDirectoryControllerTest {
    private static final Set<String> RUNTIME_ACCESS = Set.of("system.runtime.access");

    private InMemoryDirectoryRepository repository;
    private MemberDirectoryController controller;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDirectoryRepository(List.of(
                candidate(10, 20, 101, "alice-2", "Alice", true, true),
                candidate(10, 20, 100, "alice-1", "Alice", true, true),
                candidate(10, 20, 102, "bob-code", "Bob", true, true),
                candidate(10, 20, 103, "inactive-edge", "Hidden Edge", false, true),
                candidate(10, 20, 104, "inactive-member", "Hidden Member", true, false),
                candidate(10, 21, 105, "other-tenant", "Other Tenant", true, true),
                candidate(11, 20, 106, "other-system", "Other System", true, true)
        ));
        controller = new MemberDirectoryController(new MemberDirectoryService(repository));
    }

    @Test
    void requiresAuthenticationMatchingSystemTenantMemberAndRuntimePermission() {
        assertCode(() -> members(null, null, null, null), "AUTH_REQUIRED");
        assertCode(() -> members(session(
                ContextType.SYSTEM, 11L, 20L, 200L, RUNTIME_ACCESS), null, null, null),
                "CONTEXT_SYSTEM_MISMATCH");
        assertCode(() -> members(session(
                ContextType.PLATFORM, null, null, null, RUNTIME_ACCESS), null, null, null),
                "CONTEXT_SYSTEM_MISMATCH");
        assertCode(() -> members(session(
                ContextType.SYSTEM, 10L, null, 200L, RUNTIME_ACCESS), null, null, null),
                "CONTEXT_TENANT_REQUIRED");
        assertCode(() -> members(session(
                ContextType.SYSTEM, 10L, 20L, null, RUNTIME_ACCESS), null, null, null),
                "CONTEXT_MEMBER_REQUIRED");
        assertCode(() -> members(session(
                ContextType.SYSTEM, 10L, 20L, 200L, Set.of()), null, null, null),
                "PERMISSION_DENIED");

        assertThat(repository.countCalls).isZero();
        assertThat(repository.findCalls).isZero();
    }

    @Test
    void filtersInactiveAndCrossTenantMembersAndReturnsOnlyTheFrozenDto() {
        var response = members(activeSession(), null, null, null);
        var page = response.data();

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.requestId()).isEqualTo("request-1");
        assertThat(response.traceId()).isEqualTo("trace-1");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(MemberDirectoryApi.Member::memberId)
                .containsExactly("100", "101", "102");
        assertThat(page.items().getFirst())
                .isEqualTo(new MemberDirectoryApi.Member("100", "alice-1", "Alice"));
        assertThat(MemberDirectoryApi.Member.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("memberId", "memberCode", "displayName");
        assertThat(repository.lastSystemId).isEqualTo(10);
        assertThat(repository.lastTenantId).isEqualTo(20);
    }

    @Test
    void searchesMemberCodeOrDisplayNameAndUsesStablePagination() {
        var alice = members(activeSession(), " ALICE ", 2, 1).data();

        assertThat(alice.page()).isEqualTo(2);
        assertThat(alice.size()).isEqualTo(1);
        assertThat(alice.total()).isEqualTo(2);
        assertThat(alice.items()).extracting(MemberDirectoryApi.Member::memberId)
                .containsExactly("101");

        var bob = members(activeSession(), "BOB-CODE", null, null).data();
        assertThat(bob.total()).isEqualTo(1);
        assertThat(bob.items()).extracting(MemberDirectoryApi.Member::memberId)
                .containsExactly("102");
        assertThat(repository.lastKeyword).isEqualTo("bob-code");
    }

    @Test
    void capsSizeAtFiftyAndKeepsOutOfRangePagesEmptyWithTheTrueTotal() {
        var page = members(activeSession(), null, 99, 500).data();

        assertThat(page.page()).isEqualTo(99);
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).isEmpty();
        assertThat(repository.findCalls).isZero();
    }

    private com.unique.examine.core.api.ApiResponse<MemberDirectoryApi.Page> members(
            AuthenticatedSession session,
            String keyword,
            Integer page,
            Integer size
    ) {
        var request = new MockHttpServletRequest();
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-1");
        return controller.members(10, keyword, page, size, session, request);
    }

    private static AuthenticatedSession activeSession() {
        return session(ContextType.SYSTEM, 10L, 20L, 200L, RUNTIME_ACCESS);
    }

    private static AuthenticatedSession session(
            ContextType type,
            Long systemId,
            Long tenantId,
            Long memberId,
            Set<String> permissions
    ) {
        return new AuthenticatedSession(
                1L, 2L, type, systemId, tenantId, memberId, 1L, permissions);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo(code));
    }

    private static Candidate candidate(
            long systemId,
            long tenantId,
            long memberId,
            String memberCode,
            String displayName,
            boolean activeMembership,
            boolean activeMember
    ) {
        return new Candidate(
                systemId,
                tenantId,
                Long.toString(memberId),
                memberCode,
                displayName,
                activeMembership,
                activeMember
        );
    }

    private record Candidate(
            long systemId,
            long tenantId,
            String memberId,
            String memberCode,
            String displayName,
            boolean activeMembership,
            boolean activeMember
    ) {
        MemberDirectoryApi.Member member() {
            return new MemberDirectoryApi.Member(memberId, memberCode, displayName);
        }
    }

    private static final class InMemoryDirectoryRepository implements MemberDirectoryRepository {
        private final List<Candidate> candidates;
        private int countCalls;
        private int findCalls;
        private long lastSystemId;
        private long lastTenantId;
        private String lastKeyword;

        private InMemoryDirectoryRepository(List<Candidate> candidates) {
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public long count(long systemId, long tenantId, String keyword) {
            countCalls++;
            remember(systemId, tenantId, keyword);
            return eligible(systemId, tenantId, keyword).size();
        }

        @Override
        public List<MemberDirectoryApi.Member> find(
                long systemId,
                long tenantId,
                String keyword,
                long offset,
                int limit
        ) {
            findCalls++;
            remember(systemId, tenantId, keyword);
            var eligible = eligible(systemId, tenantId, keyword);
            var start = Math.min(Math.toIntExact(offset), eligible.size());
            var end = Math.min(start + limit, eligible.size());
            return new ArrayList<>(eligible.subList(start, end));
        }

        private List<MemberDirectoryApi.Member> eligible(long systemId, long tenantId, String keyword) {
            return candidates.stream()
                    .filter(candidate -> candidate.systemId == systemId)
                    .filter(candidate -> candidate.tenantId == tenantId)
                    .filter(Candidate::activeMembership)
                    .filter(Candidate::activeMember)
                    .filter(candidate -> matches(candidate, keyword))
                    .sorted(Comparator.comparing(Candidate::displayName)
                            .thenComparing(candidate -> Long.parseLong(candidate.memberId)))
                    .map(Candidate::member)
                    .toList();
        }

        private static boolean matches(Candidate candidate, String keyword) {
            return keyword.isEmpty()
                    || candidate.memberCode.toLowerCase(Locale.ROOT).contains(keyword)
                    || candidate.displayName.toLowerCase(Locale.ROOT).contains(keyword);
        }

        private void remember(long systemId, long tenantId, String keyword) {
            lastSystemId = systemId;
            lastTenantId = tenantId;
            lastKeyword = keyword;
        }
    }
}
