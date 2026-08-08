package com.unique.examine.collab.recordteam;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordTeamServiceTest {
    private static final RecordTeamKey KEY = new RecordTeamKey("system-1", "tenant-1", "record-1");
    private static final RecordTeamActor MANAGER = new RecordTeamActor(
            "manager",
            Set.of(RecordTeamCapability.MANAGE_MEMBERS, RecordTeamCapability.TRANSFER_OWNERSHIP));

    private InMemoryRecordTeamRepository repository;
    private RecordTeamService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRecordTeamRepository();
        repository.create(RecordTeam.initialize(KEY, "owner-1"));
        service = new RecordTeamService(repository, new CapabilityRecordTeamAuthorizationPolicy());
    }

    @Test
    void addsChangesAndRemovesANonOwnerMember() {
        var added = service.addMember(MANAGER, KEY, "member-1", RecordTeamRole.VIEWER);
        assertThat(added.member("member-1")).contains(
                new RecordTeamMember("member-1", RecordTeamRole.VIEWER));
        assertThat(added.version()).isEqualTo(2);

        var changed = service.changeRole(
                MANAGER,
                KEY,
                "member-1",
                RecordTeamRole.COLLABORATOR);
        assertThat(changed.member("member-1")).contains(
                new RecordTeamMember("member-1", RecordTeamRole.COLLABORATOR));
        assertThat(changed.version()).isEqualTo(3);

        var removed = service.removeMember(MANAGER, KEY, "member-1");
        assertThat(removed.member("member-1")).isEmpty();
        assertThat(removed.version()).isEqualTo(4);
        assertThat(removed.owner().memberId()).isEqualTo("owner-1");
    }

    @Test
    void rejectsDuplicateAndMissingMembersWithStableCodes() {
        service.addMember(MANAGER, KEY, "member-1", RecordTeamRole.VIEWER);

        assertCode(
                () -> service.addMember(MANAGER, KEY, "member-1", RecordTeamRole.FOLLOWER),
                "RECORD_TEAM_MEMBER_DUPLICATE");
        assertCode(
                () -> service.changeRole(MANAGER, KEY, "missing", RecordTeamRole.VIEWER),
                "RECORD_TEAM_MEMBER_NOT_FOUND");
        assertCode(
                () -> service.removeMember(MANAGER, KEY, "missing"),
                "RECORD_TEAM_MEMBER_NOT_FOUND");
        assertCode(
                () -> service.changeRole(MANAGER, KEY, "member-1", null),
                "RECORD_TEAM_ROLE_REQUIRED");
    }

    @Test
    void protectsTheLastOwnerAndRequiresTheTransferOperation() {
        assertCode(
                () -> service.removeMember(MANAGER, KEY, "owner-1"),
                "RECORD_TEAM_LAST_OWNER_REQUIRED");
        assertCode(
                () -> service.changeRole(MANAGER, KEY, "owner-1", RecordTeamRole.COLLABORATOR),
                "RECORD_TEAM_LAST_OWNER_REQUIRED");
        assertCode(
                () -> service.addMember(MANAGER, KEY, "owner-2", RecordTeamRole.OWNER),
                "RECORD_TEAM_OWNER_TRANSFER_REQUIRED");

        service.addMember(MANAGER, KEY, "member-1", RecordTeamRole.VIEWER);
        assertCode(
                () -> service.changeRole(MANAGER, KEY, "member-1", RecordTeamRole.OWNER),
                "RECORD_TEAM_OWNER_TRANSFER_REQUIRED");
    }

    @Test
    void transfersOwnershipToAnExistingMemberAndRetainsThePreviousOwner() {
        service.addMember(MANAGER, KEY, "member-1", RecordTeamRole.VIEWER);

        var transferred = service.transferOwnership(MANAGER, KEY, "member-1");

        assertThat(transferred.owner()).isEqualTo(
                new RecordTeamMember("member-1", RecordTeamRole.OWNER));
        assertThat(transferred.member("owner-1")).contains(
                new RecordTeamMember("owner-1", RecordTeamRole.COLLABORATOR));
        assertThat(transferred.members()).hasSize(2);
        assertThat(transferred.version()).isEqualTo(3);
    }

    @Test
    void transfersOwnershipToANewMemberWithoutLosingThePreviousOwner() {
        var transferred = service.transferOwnership(MANAGER, KEY, "owner-2");

        assertThat(transferred.owner().memberId()).isEqualTo("owner-2");
        assertThat(transferred.member("owner-1")).contains(
                new RecordTeamMember("owner-1", RecordTeamRole.COLLABORATOR));
        assertThat(transferred.members()).hasSize(2);
        assertCode(
                () -> service.transferOwnership(MANAGER, KEY, "owner-2"),
                "RECORD_TEAM_TRANSFER_TARGET_ALREADY_OWNER");
    }

    @Test
    void enforcesTheTwoHundredMemberLimit() {
        for (var index = 1; index < RecordTeam.MAX_MEMBERS; index++) {
            service.addMember(MANAGER, KEY, "member-" + index, RecordTeamRole.FOLLOWER);
        }
        assertThat(repository.find(KEY).orElseThrow().members()).hasSize(RecordTeam.MAX_MEMBERS);

        assertCode(
                () -> service.addMember(MANAGER, KEY, "member-over-limit", RecordTeamRole.VIEWER),
                "RECORD_TEAM_MEMBER_LIMIT_EXCEEDED");
        assertCode(
                () -> service.transferOwnership(MANAGER, KEY, "new-owner-over-limit"),
                "RECORD_TEAM_MEMBER_LIMIT_EXCEEDED");
    }

    @Test
    void appliesSeparateCapabilitiesForMemberManagementAndOwnershipTransfer() {
        var memberManager = new RecordTeamActor(
                "member-manager",
                Set.of(RecordTeamCapability.MANAGE_MEMBERS));
        service.addMember(memberManager, KEY, "member-1", RecordTeamRole.VIEWER);
        assertCode(
                () -> service.transferOwnership(memberManager, KEY, "member-1"),
                "RECORD_TEAM_PERMISSION_DENIED");

        var transferOnly = new RecordTeamActor(
                "transfer-manager",
                Set.of(RecordTeamCapability.TRANSFER_OWNERSHIP));
        service.transferOwnership(transferOnly, KEY, "member-1");
        assertCode(
                () -> service.removeMember(transferOnly, KEY, "owner-1"),
                "RECORD_TEAM_PERMISSION_DENIED");
    }

    @Test
    void initializesWithTheActorAsOwnerAndReturnsTheExistingTeamIdempotently() {
        var missingKey = new RecordTeamKey("system-1", "tenant-1", "record-2");

        var initialized = service.initialize(MANAGER, missingKey);
        var repeated = service.initialize(
                new RecordTeamActor(
                        "another-manager",
                        Set.of(RecordTeamCapability.MANAGE_MEMBERS)),
                missingKey);

        assertThat(initialized.created()).isTrue();
        assertThat(initialized.team().owner().memberId()).isEqualTo(MANAGER.memberId());
        assertThat(initialized.team().version()).isEqualTo(1);
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.team()).isEqualTo(initialized.team());
        assertThat(repeated.team().owner().memberId()).isEqualTo(MANAGER.memberId());
    }

    @Test
    void initializationRequiresMemberManagementAndDoesNotPretendToCheckRecordExistence() {
        var arbitraryRecordKey = new RecordTeamKey("system-1", "tenant-1", "not-runtime-validated");
        var actorWithoutManagement = new RecordTeamActor("viewer", Set.of());

        assertCode(
                () -> service.initialize(actorWithoutManagement, arbitraryRecordKey),
                "RECORD_TEAM_PERMISSION_DENIED");
        assertThat(repository.find(arbitraryRecordKey)).isEmpty();

        var initialized = service.initialize(MANAGER, arbitraryRecordKey);
        assertThat(initialized.created()).isTrue();
        assertThat(repository.find(arbitraryRecordKey)).contains(initialized.team());
    }

    @Test
    void reportsMissingTeamsAndDuplicateTeamInitialization() {
        assertCode(
                () -> service.addMember(
                        MANAGER,
                        new RecordTeamKey("system-1", "tenant-1", "missing"),
                        "member-1",
                        RecordTeamRole.VIEWER),
                "RECORD_TEAM_NOT_FOUND");
        assertCode(
                () -> repository.create(RecordTeam.initialize(KEY, "owner-2")),
                "RECORD_TEAM_DUPLICATE");
    }

    private static void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
