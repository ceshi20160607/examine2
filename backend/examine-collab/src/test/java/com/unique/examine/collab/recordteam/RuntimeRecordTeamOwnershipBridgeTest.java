package com.unique.examine.collab.recordteam;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RuntimeRecordTeamOwnershipBridgeTest {
    @Test
    void locksInNumericOrderAndTransfersExistingTeamWithoutLosingFormerOwner() {
        var repository = new CountingRepository();
        repository.create(RecordTeam.restore(
                key(20),
                List.of(
                        new RecordTeamMember("10", RecordTeamRole.OWNER),
                        new RecordTeamMember("11", RecordTeamRole.VIEWER)),
                4));
        repository.create(RecordTeam.initialize(key(3), "10"));
        var bridge = new RuntimeRecordTeamOwnershipBridge(repository);

        var locked = bridge.lockExistingTeams(1, 2, List.of(20L, 3L), 11);
        bridge.transferOwnership(1, 2, 20, 10, 11, 10);

        assertThat(locked).extracting(state -> state.recordId())
                .containsExactly(3L, 20L);
        assertThat(locked.get(1).targetMemberPresent()).isTrue();
        var changed = repository.find(key(20)).orElseThrow();
        assertThat(changed.owner().memberId()).isEqualTo("11");
        assertThat(changed.member("10")).contains(
                new RecordTeamMember("10", RecordTeamRole.COLLABORATOR));
        assertThat(changed.version()).isEqualTo(5);
        assertThat(repository.initializeCalls).isZero();
    }

    @Test
    void initializesMissingTeamFromHeaderThenTransfersAndRejectsOwnerDrift() {
        var repository = new InMemoryRecordTeamRepository();
        var bridge = new RuntimeRecordTeamOwnershipBridge(repository);

        bridge.transferOwnership(1, 2, 7, 10, 11, 10);
        var initialized = repository.find(key(7)).orElseThrow();
        assertThat(initialized.owner().memberId()).isEqualTo("11");
        assertThat(initialized.member("10")).contains(
                new RecordTeamMember("10", RecordTeamRole.COLLABORATOR));
        assertThat(initialized.version()).isEqualTo(2);

        repository.create(RecordTeam.initialize(key(8), "99"));
        var exception = catchThrowableOfType(
                () -> bridge.transferOwnership(1, 2, 8, 10, 11, 10),
                BusinessException.class);
        assertThat(exception.code()).isEqualTo("RECORD_TEAM_OWNER_INVARIANT_VIOLATION");
        assertThat(repository.find(key(8)).orElseThrow().owner().memberId()).isEqualTo("99");
    }

    @Test
    void ownershipCallsRequireTheCallersTransaction() throws Exception {
        var lock = RuntimeRecordTeamOwnershipBridge.class.getMethod(
                "lockExistingTeams", long.class, long.class, List.class, long.class);
        var transfer = RuntimeRecordTeamOwnershipBridge.class.getMethod(
                "transferOwnership",
                long.class, long.class, long.class, long.class, long.class, long.class);
        assertThat(lock.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(transfer.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
    }

    private static RecordTeamKey key(long recordId) {
        return new RecordTeamKey("1", "2", Long.toString(recordId));
    }

    private static final class CountingRepository implements RecordTeamRepository {
        private final InMemoryRecordTeamRepository delegate = new InMemoryRecordTeamRepository();
        private int initializeCalls;

        @Override
        public void create(RecordTeam team) {
            delegate.create(team);
        }

        @Override
        public RecordTeamInitialization initialize(RecordTeam team) {
            initializeCalls++;
            return delegate.initialize(team);
        }

        @Override
        public java.util.Optional<RecordTeam> find(RecordTeamKey key) {
            return delegate.find(key);
        }

        @Override
        public List<RecordTeam> lockAll(List<RecordTeamKey> keys) {
            return delegate.lockAll(keys);
        }

        @Override
        public RecordTeam update(
                RecordTeamKey key,
                String actorMemberId,
                java.util.function.UnaryOperator<RecordTeam> mutation
        ) {
            return delegate.update(key, actorMemberId, mutation);
        }
    }
}
