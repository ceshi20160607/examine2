package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalTaskStatus;

public final class JdbcApprovalSql {
    private JdbcApprovalSql() {
    }

    public static final String INSERT_DRAFT = """
            INSERT INTO un_flow_definition_draft
              (system_id,tenant_id,definition_id,name,approver_id,approval_mode,
               trigger_module_code,trigger_event,trigger_priority,trigger_exclusive,
               trigger_conditions,trigger_start_at,trigger_interval_minutes,trigger_requester_id,
               gateway_branches,parallel_branches,inclusive_branches,approver_sources,quorum_rules,deadline_policies,
               decision_comment_policies,decision_evidence_policies,completion_failure_policy,completion_steps,approval_stages,
               status_field_code,status_approved_value,status_rejected_value,
               status_withdrawn_value,status_terminated_value,revision,updated_at,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String UPDATE_DRAFT = """
            UPDATE un_flow_definition_draft
            SET name=?,approver_id=?,approval_mode=?,trigger_module_code=?,trigger_event=?,
                trigger_priority=?,trigger_exclusive=?,trigger_conditions=?,
                trigger_start_at=?,trigger_interval_minutes=?,trigger_requester_id=?,
                gateway_branches=?,parallel_branches=?,inclusive_branches=?,approver_sources=?,quorum_rules=?,deadline_policies=?,
                decision_comment_policies=?,decision_evidence_policies=?,completion_failure_policy=?,completion_steps=?,approval_stages=?,
                status_field_code=?,status_approved_value=?,status_rejected_value=?,
                status_withdrawn_value=?,status_terminated_value=?,
                revision=?,updated_at=?
            WHERE system_id=? AND tenant_id=? AND definition_id=? AND revision=?
            """;

    public static final String SELECT_DRAFT = """
            SELECT definition_id,name,approver_id,approval_mode,revision,updated_at,
                   trigger_module_code,trigger_event,trigger_priority,trigger_exclusive,
                   trigger_conditions,trigger_start_at,trigger_interval_minutes,
                   trigger_requester_id,gateway_branches,parallel_branches,inclusive_branches,
                   approver_sources,quorum_rules,deadline_policies,decision_comment_policies,
                   decision_evidence_policies,completion_failure_policy,completion_steps,approval_stages,
                   status_field_code,status_approved_value,status_rejected_value,
                   status_withdrawn_value,status_terminated_value,
                   (SELECT GROUP_CONCAT(step.approver_id ORDER BY step.step_no SEPARATOR ',')
                      FROM un_flow_definition_draft_step step
                     WHERE step.system_id=un_flow_definition_draft.system_id
                       AND step.tenant_id=un_flow_definition_draft.tenant_id
                       AND step.definition_id=un_flow_definition_draft.definition_id) AS approver_ids
            FROM un_flow_definition_draft
            WHERE system_id=? AND tenant_id=? AND definition_id=?
            """;

    public static final String SELECT_DRAFTS = """
            SELECT definition_id,name,approver_id,approval_mode,revision,updated_at,
                   trigger_module_code,trigger_event,trigger_priority,trigger_exclusive,
                   trigger_conditions,trigger_start_at,trigger_interval_minutes,
                   trigger_requester_id,gateway_branches,parallel_branches,inclusive_branches,
                   approver_sources,quorum_rules,deadline_policies,decision_comment_policies,
                   decision_evidence_policies,completion_failure_policy,completion_steps,approval_stages,
                   status_field_code,status_approved_value,status_rejected_value,
                   status_withdrawn_value,status_terminated_value,
                   (SELECT GROUP_CONCAT(step.approver_id ORDER BY step.step_no SEPARATOR ',')
                      FROM un_flow_definition_draft_step step
                     WHERE step.system_id=un_flow_definition_draft.system_id
                       AND step.tenant_id=un_flow_definition_draft.tenant_id
                       AND step.definition_id=un_flow_definition_draft.definition_id) AS approver_ids
            FROM un_flow_definition_draft
            WHERE system_id=? AND tenant_id=?
            ORDER BY updated_at DESC,definition_id DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_DRAFTS = """
            SELECT COUNT(*)
            FROM un_flow_definition_draft
            WHERE system_id=? AND tenant_id=?
            """;

    public static final String DELETE_DRAFT_STEPS = """
            DELETE FROM un_flow_definition_draft_step
            WHERE system_id=? AND tenant_id=? AND definition_id=?
            """;

    public static final String INSERT_DRAFT_STEP = """
            INSERT INTO un_flow_definition_draft_step
              (system_id,tenant_id,definition_id,step_no,approver_id)
            VALUES (?,?,?,?,?)
            """;

    public static final String INSERT_VERSION = """
            INSERT INTO un_flow_definition_version
              (system_id,tenant_id,definition_id,version_no,name,approver_id,approval_mode,
               trigger_module_code,trigger_event,trigger_priority,trigger_exclusive,
               trigger_conditions,trigger_start_at,trigger_interval_minutes,trigger_requester_id,
               gateway_branches,parallel_branches,inclusive_branches,approver_sources,quorum_rules,deadline_policies,
               decision_comment_policies,decision_evidence_policies,completion_failure_policy,completion_steps,approval_stages,
               status_field_code,status_approved_value,status_rejected_value,
               status_withdrawn_value,status_terminated_value,source_revision,published_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String SELECT_VERSION = """
            SELECT definition_id,version_no,name,approver_id,approval_mode,source_revision,published_at,
                   trigger_module_code,trigger_event,trigger_priority,trigger_exclusive,
                   trigger_conditions,trigger_start_at,trigger_interval_minutes,
                   trigger_requester_id,gateway_branches,parallel_branches,inclusive_branches,
                   approver_sources,quorum_rules,deadline_policies,decision_comment_policies,
                   decision_evidence_policies,completion_failure_policy,completion_steps,approval_stages,
                   status_field_code,status_approved_value,status_rejected_value,
                   status_withdrawn_value,status_terminated_value,
                   (SELECT GROUP_CONCAT(step.approver_id ORDER BY step.step_no SEPARATOR ',')
                      FROM un_flow_definition_version_step step
                     WHERE step.system_id=un_flow_definition_version.system_id
                       AND step.tenant_id=un_flow_definition_version.tenant_id
                       AND step.definition_id=un_flow_definition_version.definition_id
                       AND step.version_no=un_flow_definition_version.version_no) AS approver_ids
            FROM un_flow_definition_version
            WHERE system_id=? AND tenant_id=? AND definition_id=? AND version_no=?
            """;

    public static final String SELECT_LATEST_VERSION = """
            SELECT definition_id,version_no,name,approver_id,approval_mode,source_revision,published_at,
                   trigger_module_code,trigger_event,trigger_priority,trigger_exclusive,
                   trigger_conditions,trigger_start_at,trigger_interval_minutes,
                   trigger_requester_id,gateway_branches,parallel_branches,inclusive_branches,
                   approver_sources,quorum_rules,deadline_policies,decision_comment_policies,
                   decision_evidence_policies,completion_failure_policy,completion_steps,approval_stages,
                   status_field_code,status_approved_value,status_rejected_value,
                   status_withdrawn_value,status_terminated_value,
                   (SELECT GROUP_CONCAT(step.approver_id ORDER BY step.step_no SEPARATOR ',')
                      FROM un_flow_definition_version_step step
                     WHERE step.system_id=un_flow_definition_version.system_id
                       AND step.tenant_id=un_flow_definition_version.tenant_id
                       AND step.definition_id=un_flow_definition_version.definition_id
                       AND step.version_no=un_flow_definition_version.version_no) AS approver_ids
            FROM un_flow_definition_version
            WHERE system_id=? AND tenant_id=? AND definition_id=?
            ORDER BY version_no DESC
            LIMIT 1
            """;

    public static final String SELECT_VERSIONS = """
            SELECT definition_id,version_no,name,approver_id,approval_mode,source_revision,published_at,
                   trigger_module_code,trigger_event,trigger_priority,trigger_exclusive,
                   trigger_conditions,trigger_start_at,trigger_interval_minutes,
                   trigger_requester_id,gateway_branches,parallel_branches,inclusive_branches,
                   approver_sources,quorum_rules,deadline_policies,decision_comment_policies,
                   decision_evidence_policies,completion_failure_policy,completion_steps,approval_stages,
                   status_field_code,status_approved_value,status_rejected_value,
                   status_withdrawn_value,status_terminated_value,
                   (SELECT GROUP_CONCAT(step.approver_id ORDER BY step.step_no SEPARATOR ',')
                      FROM un_flow_definition_version_step step
                     WHERE step.system_id=un_flow_definition_version.system_id
                       AND step.tenant_id=un_flow_definition_version.tenant_id
                       AND step.definition_id=un_flow_definition_version.definition_id
                       AND step.version_no=un_flow_definition_version.version_no) AS approver_ids
            FROM un_flow_definition_version
            WHERE system_id=? AND tenant_id=? AND definition_id=?
            ORDER BY version_no DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_VERSIONS = """
            SELECT COUNT(*)
            FROM un_flow_definition_version
            WHERE system_id=? AND tenant_id=? AND definition_id=?
            """;

    public static final String SELECT_STARTABLE_DEFINITIONS = """
            SELECT version_row.definition_id,version_row.version_no,version_row.name,
                   version_row.approver_id,version_row.approval_mode,
                   version_row.source_revision,version_row.published_at,
                   version_row.trigger_module_code,version_row.trigger_event,
                   version_row.trigger_priority,version_row.trigger_exclusive,
                   version_row.trigger_conditions,version_row.trigger_start_at,
                   version_row.trigger_interval_minutes,version_row.trigger_requester_id,
                   version_row.gateway_branches,version_row.parallel_branches,
                   version_row.inclusive_branches,version_row.approver_sources,
                   version_row.quorum_rules,version_row.deadline_policies,
                   version_row.decision_comment_policies,
                   version_row.decision_evidence_policies,version_row.completion_failure_policy,version_row.completion_steps,
                   version_row.approval_stages,
                   version_row.status_field_code,version_row.status_approved_value,
                   version_row.status_rejected_value,version_row.status_withdrawn_value,
                   version_row.status_terminated_value,
                   (SELECT GROUP_CONCAT(step.approver_id ORDER BY step.step_no SEPARATOR ',')
                      FROM un_flow_definition_version_step step
                     WHERE step.system_id=version_row.system_id
                       AND step.tenant_id=version_row.tenant_id
                       AND step.definition_id=version_row.definition_id
                       AND step.version_no=version_row.version_no) AS approver_ids
            FROM un_flow_definition_version version_row
            JOIN (
                SELECT definition_id,MAX(version_no) AS version_no
                FROM un_flow_definition_version
                WHERE system_id=? AND tenant_id=?
                GROUP BY definition_id
            ) latest
              ON latest.definition_id=version_row.definition_id
             AND latest.version_no=version_row.version_no
            WHERE version_row.system_id=? AND version_row.tenant_id=?
            ORDER BY version_row.published_at DESC,version_row.definition_id DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_STARTABLE_DEFINITIONS = """
            SELECT COUNT(DISTINCT definition_id)
            FROM un_flow_definition_version
            WHERE system_id=? AND tenant_id=?
            """;

    public static final String SELECT_TRIGGER_CANDIDATES = """
            SELECT version_row.definition_id,version_row.version_no,version_row.name,
                   version_row.approver_id,version_row.approval_mode,
                   version_row.source_revision,version_row.published_at,
                   version_row.trigger_module_code,version_row.trigger_event,
                   version_row.trigger_priority,version_row.trigger_exclusive,
                   version_row.trigger_conditions,version_row.trigger_start_at,
                   version_row.trigger_interval_minutes,version_row.trigger_requester_id,
                   version_row.gateway_branches,version_row.parallel_branches,
                   version_row.inclusive_branches,version_row.approver_sources,
                   version_row.quorum_rules,version_row.deadline_policies,
                   version_row.decision_comment_policies,
                   version_row.decision_evidence_policies,version_row.completion_failure_policy,version_row.completion_steps,
                   version_row.approval_stages,
                   version_row.status_field_code,version_row.status_approved_value,
                   version_row.status_rejected_value,version_row.status_withdrawn_value,
                   version_row.status_terminated_value,
                   (SELECT GROUP_CONCAT(step.approver_id ORDER BY step.step_no SEPARATOR ',')
                      FROM un_flow_definition_version_step step
                     WHERE step.system_id=version_row.system_id
                       AND step.tenant_id=version_row.tenant_id
                       AND step.definition_id=version_row.definition_id
                       AND step.version_no=version_row.version_no) AS approver_ids
            FROM un_flow_definition_version version_row
            JOIN (
                SELECT definition_id,MAX(version_no) AS version_no
                FROM un_flow_definition_version
                WHERE system_id=? AND tenant_id=?
                GROUP BY definition_id
            ) latest
              ON latest.definition_id=version_row.definition_id
             AND latest.version_no=version_row.version_no
            WHERE version_row.system_id=? AND version_row.tenant_id=?
              AND version_row.trigger_module_code=?
              AND version_row.trigger_event=?
            ORDER BY version_row.trigger_priority DESC,version_row.definition_id ASC
            """;

    public static final String INSERT_VERSION_STEP = """
            INSERT INTO un_flow_definition_version_step
              (system_id,tenant_id,definition_id,version_no,step_no,approver_id)
            VALUES (?,?,?,?,?,?)
            """;

    public static final String INSERT_INSTANCE = """
            INSERT INTO un_flow_instance
              (system_id,tenant_id,instance_id,definition_id,definition_version,business_key,
               module_code,record_id,requester_id,approver_id,approver_ids_json,current_step_index,claim_state,
               approval_mode,required_approvals,deadline_policy,deadline_remind_at,deadline_due_at,
                deadline_reminded_at,deadline_processed_at,decision_comment_policy,
                decision_evidence_policy,
                approval_stage_state,start_context,current_stage_index,
                approved_approver_ids_json,rejected_approver_ids_json,
                status,completion_phase,completion_failure_policy,active_completion_ordinal,
                state_version,started_at,completed_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String UPDATE_INSTANCE_DECISION = """
            UPDATE un_flow_instance
            SET approver_id=?,approver_ids_json=?,current_step_index=?,claim_state=?,
                approval_mode=?,required_approvals=?,deadline_policy=?,deadline_remind_at=?,deadline_due_at=?,
                deadline_reminded_at=?,deadline_processed_at=?,decision_comment_policy=?,
                decision_evidence_policy=?,
                approval_stage_state=?,current_stage_index=?,
                approved_approver_ids_json=?,rejected_approver_ids_json=?,
                status=?,completion_phase=?,active_completion_ordinal=?,
                state_version=?,completed_at=?
            WHERE system_id=? AND tenant_id=? AND instance_id=?
              AND status='PENDING' AND state_version=?
            """;

    public static final String UPDATE_INSTANCE_COMPLETION_PROGRESS = """
            UPDATE un_flow_instance
            SET completion_phase=?,active_completion_ordinal=?
            WHERE system_id=? AND tenant_id=? AND instance_id=?
              AND status='PENDING'
              AND completion_phase IN ('EXTERNAL_EXECUTION','COMPENSATING')
              AND state_version=?
            """;

    public static final String INSERT_PARALLEL_BRANCH = """
            INSERT INTO un_flow_parallel_branch_execution
              (system_id,tenant_id,instance_id,branch_code,branch_name,branch_order,
               approver_id,approver_ids_json,current_step_index,approval_mode,required_approvals,
                deadline_policy,deadline_remind_at,deadline_due_at,deadline_reminded_at,deadline_processed_at,
                decision_comment_policy,decision_evidence_policy,
                approval_stage_state,current_stage_index,
                approved_approver_ids_json,rejected_approver_ids_json,status,
                started_at,completed_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String UPDATE_PARALLEL_BRANCH = """
            UPDATE un_flow_parallel_branch_execution
            SET approver_id=?,approver_ids_json=?,current_step_index=?,
                approval_mode=?,required_approvals=?,
                deadline_policy=?,deadline_remind_at=?,deadline_due_at=?,
                deadline_reminded_at=?,deadline_processed_at=?,decision_comment_policy=?,
                decision_evidence_policy=?,
                approval_stage_state=?,current_stage_index=?,
                approved_approver_ids_json=?,rejected_approver_ids_json=?,
                status=?,completed_at=?
            WHERE system_id=? AND tenant_id=? AND instance_id=? AND branch_code=?
            """;

    public static final String SELECT_PARALLEL_BRANCHES = """
            SELECT branch_code,branch_name,branch_order,approver_id,
                   approver_ids_json AS approver_ids,current_step_index,approval_mode,required_approvals,
                   deadline_policy,deadline_remind_at,deadline_due_at,
                   deadline_reminded_at,deadline_processed_at,decision_comment_policy,
                   decision_evidence_policy,
                   approval_stage_state,current_stage_index,
                   approved_approver_ids_json,rejected_approver_ids_json,
                   status,started_at,completed_at
            FROM un_flow_parallel_branch_execution
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            ORDER BY branch_order
            """;

    public static final String SELECT_INSTANCE = """
            SELECT instance_id,definition_id,definition_version,business_key,requester_id,approver_id,
                   status,completion_phase,completion_failure_policy,active_completion_ordinal,
                   started_at,completed_at,approver_ids_json AS approver_ids,
                   current_step_index,claim_state,module_code,record_id,
                   approval_mode,required_approvals,deadline_policy,deadline_remind_at,deadline_due_at,
                   deadline_reminded_at,deadline_processed_at,decision_comment_policy,
                   decision_evidence_policy,
                   approval_stage_state,start_context,current_stage_index,
                   approved_approver_ids_json,rejected_approver_ids_json
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            """;

    public static final String SELECT_INSTANCE_FOR_UPDATE =
            SELECT_INSTANCE + "\nFOR UPDATE";

    public static final String SELECT_INSTANCES = """
            SELECT instance_id,definition_id,definition_version,business_key,requester_id,approver_id,
                   status,completion_phase,completion_failure_policy,active_completion_ordinal,
                   started_at,completed_at,approver_ids_json AS approver_ids,
                   current_step_index,claim_state,module_code,record_id,
                   approval_mode,required_approvals,deadline_policy,deadline_remind_at,deadline_due_at,
                   deadline_reminded_at,deadline_processed_at,decision_comment_policy,
                   decision_evidence_policy,
                   approval_stage_state,start_context,current_stage_index,
                   approved_approver_ids_json,rejected_approver_ids_json
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
            ORDER BY started_at DESC,instance_id DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_INSTANCES = """
            SELECT COUNT(*)
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
            """;

    public static final String SELECT_INSTANCES_FILTERED = """
            SELECT instance_id,definition_id,definition_version,business_key,requester_id,approver_id,
                   status,completion_phase,completion_failure_policy,active_completion_ordinal,
                   started_at,completed_at,approver_ids_json AS approver_ids,
                   current_step_index,claim_state,module_code,record_id,
                   approval_mode,required_approvals,deadline_policy,deadline_remind_at,deadline_due_at,
                   deadline_reminded_at,deadline_processed_at,decision_comment_policy,
                   decision_evidence_policy,
                   approval_stage_state,start_context,current_stage_index,
                   approved_approver_ids_json,rejected_approver_ids_json
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
              AND (? IS NULL OR status=?)
              AND (? IS NULL OR completed_at>=?)
              AND (? IS NULL OR completed_at<?)
            ORDER BY started_at DESC,instance_id DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_INSTANCES_FILTERED = """
            SELECT COUNT(*)
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
              AND (? IS NULL OR status=?)
              AND (? IS NULL OR completed_at>=?)
              AND (? IS NULL OR completed_at<?)
            """;

    public static final String SELECT_APPROVAL_TASKS = """
            SELECT instance_id,definition_id,definition_version,business_key,requester_id,approver_id,
                   status,completion_phase,completion_failure_policy,active_completion_ordinal,
                   started_at,completed_at,approver_ids_json AS approver_ids,
                   current_step_index,claim_state,module_code,record_id,
                   approval_mode,required_approvals,deadline_policy,deadline_remind_at,deadline_due_at,
                   deadline_reminded_at,deadline_processed_at,decision_comment_policy,
                   decision_evidence_policy,
                   approval_stage_state,start_context,current_stage_index,
                   approved_approver_ids_json,rejected_approver_ids_json
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
              AND (status<>'PENDING'
                OR completion_phase='HUMAN_APPROVAL')
              AND (
                (
                  NOT EXISTS (
                    SELECT 1 FROM un_flow_parallel_branch_execution parallel_branch
                    WHERE parallel_branch.system_id=un_flow_instance.system_id
                      AND parallel_branch.tenant_id=un_flow_instance.tenant_id
                      AND parallel_branch.instance_id=un_flow_instance.instance_id
                  )
                  AND (
                (
                  status='PENDING'
                  AND (
                    (approval_mode='SEQUENTIAL' AND approver_id=?)
                    OR (
                      approval_mode IN ('ANY','ALL','QUORUM')
                      AND JSON_CONTAINS(approver_ids_json,CAST(? AS JSON),'$')=1
                      AND JSON_CONTAINS(approved_approver_ids_json,CAST(? AS JSON),'$')=0
                      AND JSON_CONTAINS(rejected_approver_ids_json,CAST(? AS JSON),'$')=0
                    )
                  )
                )
                OR (
                  status<>'PENDING'
                  AND (
                    (approval_mode='SEQUENTIAL' AND approver_id=?)
                    OR (
                      approval_mode IN ('ANY','ALL','QUORUM')
                      AND JSON_CONTAINS(approver_ids_json,CAST(? AS JSON),'$')=1
                    )
                  )
                )
                  )
                )
                OR EXISTS (
                  SELECT 1 FROM un_flow_parallel_branch_execution parallel_branch
                  WHERE parallel_branch.system_id=un_flow_instance.system_id
                    AND parallel_branch.tenant_id=un_flow_instance.tenant_id
                    AND parallel_branch.instance_id=un_flow_instance.instance_id
                    AND (
                      (
                        un_flow_instance.status='PENDING'
                        AND parallel_branch.status='PENDING'
                        AND (
                          (parallel_branch.approval_mode='SEQUENTIAL'
                            AND parallel_branch.approver_id=?)
                          OR (
                            parallel_branch.approval_mode IN ('ANY','ALL','QUORUM')
                            AND JSON_CONTAINS(
                              parallel_branch.approver_ids_json,CAST(? AS JSON),'$')=1
                            AND JSON_CONTAINS(
                              parallel_branch.approved_approver_ids_json,CAST(? AS JSON),'$')=0
                            AND JSON_CONTAINS(
                              parallel_branch.rejected_approver_ids_json,CAST(? AS JSON),'$')=0
                          )
                        )
                      )
                      OR (
                        un_flow_instance.status<>'PENDING'
                        AND JSON_CONTAINS(
                          parallel_branch.approver_ids_json,CAST(? AS JSON),'$')=1
                      )
                    )
                )
              )
            %s
            ORDER BY started_at DESC,instance_id DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_APPROVAL_TASKS = """
            SELECT COUNT(*)
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
              AND (
                (
                  NOT EXISTS (
                    SELECT 1 FROM un_flow_parallel_branch_execution parallel_branch
                    WHERE parallel_branch.system_id=un_flow_instance.system_id
                      AND parallel_branch.tenant_id=un_flow_instance.tenant_id
                      AND parallel_branch.instance_id=un_flow_instance.instance_id
                  )
                  AND (
                (
                  status='PENDING'
                  AND (
                    (approval_mode='SEQUENTIAL' AND approver_id=?)
                    OR (
                      approval_mode IN ('ANY','ALL','QUORUM')
                      AND JSON_CONTAINS(approver_ids_json,CAST(? AS JSON),'$')=1
                      AND JSON_CONTAINS(approved_approver_ids_json,CAST(? AS JSON),'$')=0
                      AND JSON_CONTAINS(rejected_approver_ids_json,CAST(? AS JSON),'$')=0
                    )
                  )
                )
                OR (
                  status<>'PENDING'
                  AND (
                    (approval_mode='SEQUENTIAL' AND approver_id=?)
                    OR (
                      approval_mode IN ('ANY','ALL','QUORUM')
                      AND JSON_CONTAINS(approver_ids_json,CAST(? AS JSON),'$')=1
                    )
                  )
                )
                  )
                )
                OR EXISTS (
                  SELECT 1 FROM un_flow_parallel_branch_execution parallel_branch
                  WHERE parallel_branch.system_id=un_flow_instance.system_id
                    AND parallel_branch.tenant_id=un_flow_instance.tenant_id
                    AND parallel_branch.instance_id=un_flow_instance.instance_id
                    AND (
                      (
                        un_flow_instance.status='PENDING'
                        AND parallel_branch.status='PENDING'
                        AND (
                          (parallel_branch.approval_mode='SEQUENTIAL'
                            AND parallel_branch.approver_id=?)
                          OR (
                            parallel_branch.approval_mode IN ('ANY','ALL','QUORUM')
                            AND JSON_CONTAINS(
                              parallel_branch.approver_ids_json,CAST(? AS JSON),'$')=1
                            AND JSON_CONTAINS(
                              parallel_branch.approved_approver_ids_json,CAST(? AS JSON),'$')=0
                            AND JSON_CONTAINS(
                              parallel_branch.rejected_approver_ids_json,CAST(? AS JSON),'$')=0
                          )
                        )
                      )
                      OR (
                        un_flow_instance.status<>'PENDING'
                        AND JSON_CONTAINS(
                          parallel_branch.approver_ids_json,CAST(? AS JSON),'$')=1
                      )
                    )
                )
              )
            %s
            """;

    public static final String SELECT_CLAIMABLE_TASKS = """
            SELECT instance_id,definition_id,definition_version,business_key,requester_id,approver_id,
                   status,completion_phase,completion_failure_policy,active_completion_ordinal,
                   started_at,completed_at,approver_ids_json AS approver_ids,
                   current_step_index,claim_state,module_code,record_id,
                   approval_mode,required_approvals,deadline_policy,deadline_remind_at,deadline_due_at,
                   deadline_reminded_at,deadline_processed_at,decision_comment_policy,
                   decision_evidence_policy,
                   approval_stage_state,start_context,current_stage_index,
                   approved_approver_ids_json,rejected_approver_ids_json
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
              AND status='PENDING'
              AND completion_phase='HUMAN_APPROVAL'
              AND claim_state='OPEN'
            ORDER BY started_at DESC,instance_id DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_CLAIMABLE_TASKS = """
            SELECT COUNT(*)
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=?
              AND status='PENDING'
              AND completion_phase='HUMAN_APPROVAL'
              AND claim_state='OPEN'
            """;

    public static final String INSERT_DECISION_COMMENT_TEMPLATE = """
            INSERT INTO un_flow_decision_comment_template
              (system_id,tenant_id,template_id,name,status,current_version,
               created_by,created_at,updated_by,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String UPDATE_DECISION_COMMENT_TEMPLATE = """
            UPDATE un_flow_decision_comment_template
            SET name=?,status=?,current_version=?,updated_by=?,updated_at=?
            WHERE system_id=? AND tenant_id=? AND template_id=?
              AND current_version=?
            """;

    public static final String INSERT_DECISION_COMMENT_TEMPLATE_VERSION = """
            INSERT INTO un_flow_decision_comment_template_version
              (system_id,tenant_id,template_id,version_no,name,comment_body,
               created_by,created_at)
            VALUES (?,?,?,?,?,?,?,?)
            """;

    public static final String SELECT_DECISION_COMMENT_TEMPLATE = """
            SELECT template_row.template_id,template_row.name,
                   version_row.comment_body,template_row.current_version,
                   template_row.status,template_row.created_by,
                   template_row.created_at,template_row.updated_by,
                   template_row.updated_at
            FROM un_flow_decision_comment_template template_row
            JOIN un_flow_decision_comment_template_version version_row
              ON version_row.system_id=template_row.system_id
             AND version_row.tenant_id=template_row.tenant_id
             AND version_row.template_id=template_row.template_id
             AND version_row.version_no=template_row.current_version
            WHERE template_row.system_id=? AND template_row.tenant_id=?
              AND template_row.template_id=?
            """;

    public static final String SELECT_DECISION_COMMENT_TEMPLATE_FOR_UPDATE =
            SELECT_DECISION_COMMENT_TEMPLATE + "\nFOR UPDATE";

    public static final String SELECT_DECISION_COMMENT_TEMPLATE_VERSION = """
            SELECT template_id,version_no,name,comment_body,created_by,created_at
            FROM un_flow_decision_comment_template_version
            WHERE system_id=? AND tenant_id=? AND template_id=? AND version_no=?
            """;

    public static final String SELECT_DECISION_COMMENT_TEMPLATES = """
            SELECT template_row.template_id,template_row.name,
                   version_row.comment_body,template_row.current_version,
                   template_row.status,template_row.created_by,
                   template_row.created_at,template_row.updated_by,
                   template_row.updated_at
            FROM un_flow_decision_comment_template template_row
            JOIN un_flow_decision_comment_template_version version_row
              ON version_row.system_id=template_row.system_id
             AND version_row.tenant_id=template_row.tenant_id
             AND version_row.template_id=template_row.template_id
             AND version_row.version_no=template_row.current_version
            WHERE template_row.system_id=? AND template_row.tenant_id=?
            %s
            ORDER BY template_row.updated_at DESC,template_row.template_id DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_DECISION_COMMENT_TEMPLATES = """
            SELECT COUNT(*)
            FROM un_flow_decision_comment_template
            WHERE system_id=? AND tenant_id=?
            %s
            """;

    public static final String INSERT_DECISION_EVIDENCE = """
            INSERT INTO un_flow_decision_evidence
              (system_id,tenant_id,evidence_id,instance_id,history_sequence,
               branch_code,stage_index,decision,signature_kind,
               signature_file_id,typed_signature,template_id,template_version,
               template_name,actor_id,represented_member_id,delegation_id,
               decided_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String INSERT_DECISION_EVIDENCE_FILE = """
            INSERT INTO un_flow_decision_evidence_file
              (system_id,tenant_id,evidence_id,file_id,original_name,
               content_type,size_bytes,sha256,is_attachment,is_signature,
               attachment_order)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String SELECT_DECISION_EVIDENCE = """
            SELECT evidence_id,instance_id,history_sequence,branch_code,
                   stage_index,decision,signature_kind,signature_file_id,
                   typed_signature,template_id,template_version,template_name,
                   actor_id,represented_member_id,delegation_id,decided_at
            FROM un_flow_decision_evidence
            WHERE system_id=? AND tenant_id=? AND instance_id=?
              AND history_sequence=?
            """;

    public static final String SELECT_DECISION_EVIDENCE_BY_ID = """
            SELECT evidence_id,instance_id,history_sequence,branch_code,
                   stage_index,decision,signature_kind,signature_file_id,
                   typed_signature,template_id,template_version,template_name,
                   actor_id,represented_member_id,delegation_id,decided_at
            FROM un_flow_decision_evidence
            WHERE system_id=? AND tenant_id=? AND evidence_id=?
            """;

    public static final String SELECT_DECISION_EVIDENCE_BY_INSTANCE = """
            SELECT evidence_id,instance_id,history_sequence,branch_code,
                   stage_index,decision,signature_kind,signature_file_id,
                   typed_signature,template_id,template_version,template_name,
                   actor_id,represented_member_id,delegation_id,decided_at
            FROM un_flow_decision_evidence
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            ORDER BY history_sequence
            """;

    public static final String SELECT_DECISION_EVIDENCE_FILES = """
            SELECT file_id,original_name,content_type,size_bytes,sha256,
                   is_attachment,is_signature,attachment_order
            FROM un_flow_decision_evidence_file
            WHERE system_id=? AND tenant_id=? AND evidence_id=?
            ORDER BY
              CASE WHEN attachment_order IS NULL THEN 5 ELSE attachment_order END,
              file_id
            """;

    public static final String INSERT_DELEGATION = """
            INSERT INTO un_flow_approval_delegation
              (system_id,tenant_id,delegation_id,delegator_member_id,delegate_member_id,
               definition_id,starts_at,ends_at,status,created_by,created_at,
               revoked_by,revoked_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String REVOKE_DELEGATION = """
            UPDATE un_flow_approval_delegation
            SET status='REVOKED',revoked_by=?,revoked_at=?
            WHERE system_id=? AND tenant_id=? AND delegation_id=?
              AND status<>'REVOKED'
            """;

    public static final String SELECT_DELEGATION = """
            SELECT tenant_id,delegation_id,delegator_member_id,delegate_member_id,definition_id,
                   starts_at,ends_at,status,created_by,created_at,revoked_by,revoked_at
            FROM un_flow_approval_delegation
            WHERE system_id=? AND tenant_id=? AND delegation_id=?
            """;

    public static final String SELECT_DELEGATION_FOR_UPDATE = """
            SELECT tenant_id,delegation_id,delegator_member_id,delegate_member_id,definition_id,
                   starts_at,ends_at,status,created_by,created_at,revoked_by,revoked_at
            FROM un_flow_approval_delegation
            WHERE system_id=? AND tenant_id=? AND delegation_id=?
            FOR UPDATE
            """;

    public static final String SELECT_DELEGATIONS_BY_DELEGATOR = """
            SELECT tenant_id,delegation_id,delegator_member_id,delegate_member_id,definition_id,
                   starts_at,ends_at,status,created_by,created_at,revoked_by,revoked_at
            FROM un_flow_approval_delegation
            WHERE system_id=? AND tenant_id=? AND delegator_member_id=?
            ORDER BY created_at DESC,delegation_id DESC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_DELEGATIONS_BY_DELEGATOR = """
            SELECT COUNT(*)
            FROM un_flow_approval_delegation
            WHERE system_id=? AND tenant_id=? AND delegator_member_id=?
            """;

    public static final String SELECT_DELEGATION_CONFLICTS_FOR_UPDATE = """
            SELECT tenant_id,delegation_id,delegator_member_id,delegate_member_id,definition_id,
                   starts_at,ends_at,status,created_by,created_at,revoked_by,revoked_at
            FROM un_flow_approval_delegation
            WHERE system_id=? AND tenant_id=?
              AND status<>'REVOKED'
              AND starts_at<?
              AND ends_at>?
              AND (
                delegator_member_id IN (?,?)
                OR delegate_member_id IN (?,?)
              )
            ORDER BY delegation_id
            FOR UPDATE
            """;

    public static final String SELECT_ACTIVE_DELEGATION_FOR_UPDATE = """
            SELECT tenant_id,delegation_id,delegator_member_id,delegate_member_id,definition_id,
                   starts_at,ends_at,status,created_by,created_at,revoked_by,revoked_at
            FROM un_flow_approval_delegation
            WHERE system_id=? AND tenant_id=?
              AND delegate_member_id=?
              AND delegator_member_id=?
              AND status<>'REVOKED'
              AND starts_at<=?
              AND ends_at>?
              AND (definition_id IS NULL OR definition_id=?)
            ORDER BY CASE WHEN definition_id=? THEN 0 ELSE 1 END,delegation_id
            LIMIT 1
            FOR UPDATE
            """;

    public static final String SELECT_ACTIVE_DELEGATIONS_BY_DELEGATE = """
            SELECT tenant_id,delegation_id,delegator_member_id,delegate_member_id,definition_id,
                   starts_at,ends_at,status,created_by,created_at,revoked_by,revoked_at
            FROM un_flow_approval_delegation
            WHERE system_id=? AND tenant_id=?
              AND delegate_member_id=?
              AND status<>'REVOKED'
              AND starts_at<=?
              AND ends_at>?
            ORDER BY delegator_member_id,definition_id,delegation_id
            """;

    public static final String INSERT_HISTORY = """
            INSERT INTO un_flow_history_event
              (system_id,tenant_id,instance_id,event_sequence,event_type,actor_id,
               represented_member_id,delegation_id,from_status,to_status,comment,occurred_at,
               target_member_id,assignment_position,target_step_index)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    public static final String SELECT_HISTORY = """
            SELECT event_sequence,event_type,actor_id,represented_member_id,delegation_id,
                   from_status,to_status,comment,occurred_at,target_member_id,
                   assignment_position,target_step_index
            FROM un_flow_history_event
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            ORDER BY event_sequence
            """;

    public static final String SELECT_TRIGGER_DISPATCH_FOR_UPDATE = """
            SELECT event_key,definition_id,definition_version,instance_id
            FROM un_flow_trigger_dispatch
            WHERE system_id=? AND tenant_id=? AND event_key=?
            FOR UPDATE
            """;

    public static final String INSERT_TRIGGER_DISPATCH = """
            INSERT INTO un_flow_trigger_dispatch
              (system_id,tenant_id,event_key,definition_id,definition_version,instance_id)
            VALUES (?,?,?,?,?,?)
            """;

    public static final String SELECT_TRIGGER_DISPATCH_INSTANCES = """
            SELECT ordinal,definition_id,definition_version,instance_id
            FROM un_flow_trigger_dispatch_instance
            WHERE system_id=? AND tenant_id=? AND event_key=?
            ORDER BY ordinal
            """;

    public static final String INSERT_TRIGGER_DISPATCH_INSTANCE = """
            INSERT INTO un_flow_trigger_dispatch_instance
              (system_id,tenant_id,event_key,ordinal,definition_id,definition_version,instance_id)
            VALUES (?,?,?,?,?,?,?)
            """;

    static String approvalTasks(ApprovalTaskStatus status) {
        return SELECT_APPROVAL_TASKS.formatted(approvalTaskStatus(status));
    }

    static String countApprovalTasks(ApprovalTaskStatus status) {
        return COUNT_APPROVAL_TASKS.formatted(approvalTaskStatus(status));
    }

    static String approvalTaskStatus(ApprovalTaskStatus status) {
        return switch (status) {
            case PENDING -> "AND status='PENDING' AND claim_state='CLAIMED'";
            case COMPLETED -> "AND status IN ('APPROVED','REJECTED','WITHDRAWN','TERMINATED')";
            case ALL -> "AND (status<>'PENDING' OR claim_state='CLAIMED')";
        };
    }

    static String decisionCommentTemplates(boolean activeOnly) {
        return SELECT_DECISION_COMMENT_TEMPLATES.formatted(
                activeOnly ? "AND template_row.status='ACTIVE'" : "");
    }

    static String countDecisionCommentTemplates(boolean activeOnly) {
        return COUNT_DECISION_COMMENT_TEMPLATES.formatted(
                activeOnly ? "AND status='ACTIVE'" : "");
    }
}
