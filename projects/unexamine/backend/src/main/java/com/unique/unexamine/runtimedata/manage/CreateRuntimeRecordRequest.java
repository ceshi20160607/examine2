package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.List;

public record CreateRuntimeRecordRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 100) String recordNumber,
        @Size(max = 64) String status,
        Long ownerMemberId,
        Long departmentId,
        @NotNull List<Long> participantMemberIds,
        @NotNull Map<String, JsonNode> fields,
        @Valid List<RuntimeRecordParticipant> participantMembers,
        /** 显式业务创建人；未填写时由系统当前所属人补齐，真实提交者另记审计。 */
        Long createdByMemberId) {
    /** 旧调用方未指定角色时，服务端按协作人保存。 */
    public CreateRuntimeRecordRequest(String title, String recordNumber, String status, Long ownerMemberId,
            Long departmentId, List<Long> participantMemberIds, Map<String, JsonNode> fields) {
        this(title, recordNumber, status, ownerMemberId, departmentId, participantMemberIds, fields, null, null);
    }

    /** 保留已有含团队角色的调用形式，创建人继续按系统所属人缺省。 */
    public CreateRuntimeRecordRequest(String title, String recordNumber, String status, Long ownerMemberId,
            Long departmentId, List<Long> participantMemberIds, Map<String, JsonNode> fields,
            List<RuntimeRecordParticipant> participantMembers) {
        this(title, recordNumber, status, ownerMemberId, departmentId, participantMemberIds, fields,
                participantMembers, null);
    }
}
