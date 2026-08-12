-- Batch83 keeps the existing AI tool-call ledger and broadens only its
-- operation-name contract for the three bounded read-only owner tools.
ALTER TABLE un_ai_agent_tool_call
    DROP CHECK ck_ai_agent_tool_call_name,
    ADD CONSTRAINT ck_ai_agent_tool_call_name CHECK (
        tool_name IN (
            'RECORD_QUERY',
            'RECORD_CONTEXT_SUMMARY',
            'WORK_TASK_QUERY',
            'WORK_DAILY_REPORT_QUERY'
        )
    );
