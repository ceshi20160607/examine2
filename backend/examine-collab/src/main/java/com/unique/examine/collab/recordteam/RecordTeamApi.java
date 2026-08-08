package com.unique.examine.collab.recordteam;

import java.util.List;

public final class RecordTeamApi {
    private RecordTeamApi() {
    }

    public record AddMemberRequest(String memberId, RecordTeamRole role) {
    }

    public record ChangeRoleRequest(RecordTeamRole role) {
    }

    public record TransferOwnershipRequest(String targetMemberId) {
    }

    public record InitializeTeamResponse(boolean created, TeamResponse team) {
        static InitializeTeamResponse from(RecordTeamInitialization initialization) {
            return new InitializeTeamResponse(
                    initialization.created(),
                    TeamResponse.from(initialization.team()));
        }
    }

    public record MemberResponse(String memberId, String role) {
        static MemberResponse from(RecordTeamMember member) {
            return new MemberResponse(member.memberId(), member.role().name());
        }
    }

    public record TeamResponse(
            String systemId,
            String tenantId,
            String recordId,
            long version,
            String ownerMemberId,
            List<MemberResponse> members
    ) {
        public TeamResponse {
            members = List.copyOf(members);
        }

        static TeamResponse from(RecordTeam team) {
            return new TeamResponse(
                    team.key().systemId(),
                    team.key().tenantId(),
                    team.key().recordId(),
                    team.version(),
                    team.owner().memberId(),
                    team.members().stream().map(MemberResponse::from).toList());
        }
    }
}
