package com.unique.examine.collab.recordteam;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRecordTeamRepositoryTest {
    @Test
    void initializesOnceAndReturnsTheExistingTeamWithoutReplacingItsOwner() {
        var repository = new InMemoryRecordTeamRepository();
        var key = new RecordTeamKey("1", "2", "3");

        var first = repository.initialize(RecordTeam.initialize(key, "10"));
        var repeated = repository.initialize(RecordTeam.initialize(key, "11"));

        assertThat(first.created()).isTrue();
        assertThat(first.team().owner().memberId()).isEqualTo("10");
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.team()).isSameAs(first.team());
        assertThat(repeated.team().owner().memberId()).isEqualTo("10");
        assertThat(repository.find(key)).contains(first.team());
    }

    @Test
    void keepsInitializationTenantScoped() {
        var repository = new InMemoryRecordTeamRepository();
        var firstTenant = new RecordTeamKey("1", "2", "3");
        var secondTenant = new RecordTeamKey("1", "4", "3");

        assertThat(repository.initialize(RecordTeam.initialize(firstTenant, "10")).created()).isTrue();
        assertThat(repository.initialize(RecordTeam.initialize(secondTenant, "11")).created()).isTrue();
        assertThat(repository.find(firstTenant).orElseThrow().owner().memberId()).isEqualTo("10");
        assertThat(repository.find(secondTenant).orElseThrow().owner().memberId()).isEqualTo("11");
    }
}
