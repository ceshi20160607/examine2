package com.unique.examine.collab.recordteam;

public record RecordTeamMember(String memberId, RecordTeamRole role) {
    public RecordTeamMember {
        if (memberId == null || memberId.isBlank()) {
            throw RecordTeamException.badRequest(
                    "RECORD_TEAM_MEMBER_ID_REQUIRED",
                    "memberId is required");
        }
        memberId = memberId.trim();
        if (role == null) {
            throw RecordTeamException.badRequest(
                    "RECORD_TEAM_ROLE_REQUIRED",
                    "role is required");
        }
    }
}
