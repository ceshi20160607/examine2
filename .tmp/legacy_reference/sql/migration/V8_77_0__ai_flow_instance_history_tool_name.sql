-- Batch100 keeps the existing hash/count-only AI tool-call ledger and broadens
-- only the operation-name contract for bounded owner-enforced Flow history.
ALTER TABLE un_ai_agent_tool_call
    DROP CHECK ck_ai_agent_tool_call_name,
    ADD CONSTRAINT ck_ai_agent_tool_call_name CHECK (
        tool_name IN (
            'RECORD_QUERY',
            'RECORD_CONTEXT_SUMMARY',
            'WORK_TASK_QUERY',
            'WORK_DAILY_REPORT_QUERY',
            'TODO_QUERY',
            'MESSAGE_QUERY',
            'WORK_PROJECT_METRICS_QUERY',
            'RECORD_COMMENT_QUERY',
            'RECORD_HISTORY_QUERY',
            'RECORD_FILE_QUERY',
            'RUNTIME_STATISTICS_QUERY',
            'RUNTIME_REPORT_QUERY',
            'FLOW_INSTANCE_HISTORY_QUERY'
        )
    );
