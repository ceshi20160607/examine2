-- Expand only the isolated platform AI read-operation allowlists. The query
-- reuses existing quota, audit, evidence and personal-task owner tables.
ALTER TABLE un_platform_ai_turn
    DROP CHECK ck_platform_ai_turn_operation,
    ADD CONSTRAINT ck_platform_ai_turn_operation CHECK (
        operation IN (
            'UNRESOLVED', 'AUTHORIZED_SYSTEMS_QUERY',
            'SYSTEM_SWITCH_GUIDANCE', 'PLATFORM_TASK_DRAFT',
            'PLATFORM_OPERATIONS_QUERY'));

ALTER TABLE un_platform_ai_evidence
    DROP CHECK ck_platform_ai_evidence_type,
    ADD CONSTRAINT ck_platform_ai_evidence_type CHECK (
        evidence_type IN (
            'AUTHORIZED_SYSTEMS', 'SWITCH_GUIDANCE',
            'PERSONAL_TASKS', 'AI_QUOTA', 'SERVICE_HEALTH',
            'AGENT_ACTIVITY', 'OPERATIONS_CLARIFICATION'));
