-- Extend the existing Todo projection/action invariants for unread Event messages.
-- No storage shape changes: Event messages remain owner facts and Todo remains a projection.

ALTER TABLE un_todo_item
    DROP CHECK ck_todo_item_source;

ALTER TABLE un_todo_item
    ADD CONSTRAINT ck_todo_item_source CHECK (
        (source_type='WORK_TASK' AND category='TASK'
          AND available_actions='COMPLETE' AND represented_member_id IS NULL)
        OR (source_type='FLOW_APPROVAL' AND category='APPROVAL'
          AND available_actions='APPROVE,REJECT'
          AND represented_member_id IS NOT NULL AND represented_member_id>0)
        OR (source_type='EVENT_MESSAGE'
          AND category IN ('REMINDER','CC')
          AND available_actions='MARK_READ'
          AND represented_member_id IS NULL
          AND action_scope='MARK_READ'
          AND source_id REGEXP '^[1-9][0-9]{0,18}$'
          AND (CHAR_LENGTH(source_id)<19
            OR source_id<='9223372036854775807')));

ALTER TABLE un_todo_action_log
    DROP CHECK ck_todo_action_source;

ALTER TABLE un_todo_action_log
    ADD CONSTRAINT ck_todo_action_source CHECK (
        (source_type='WORK_TASK' AND requested_action='COMPLETE')
        OR (source_type='FLOW_APPROVAL'
          AND requested_action IN ('APPROVE','REJECT'))
        OR (source_type='EVENT_MESSAGE' AND requested_action='MARK_READ'));
