-- Batch87 keeps the existing hash-only AI tool-call ledger and broadens only
-- the operation-name contract for the bounded Todo and Event owner reads.
ALTER TABLE un_ai_agent_tool_call
    DROP CHECK ck_ai_agent_tool_call_name,
    ADD CONSTRAINT ck_ai_agent_tool_call_name CHECK (
        tool_name IN (
            'RECORD_QUERY',
            'RECORD_CONTEXT_SUMMARY',
            'WORK_TASK_QUERY',
            'WORK_DAILY_REPORT_QUERY',
            'TODO_QUERY',
            'MESSAGE_QUERY'
        )
    );
