package com.unique.examine.flow.interaction.jdbc;

public final class JdbcFlowInteractionSql {
    public static final String INSERT_URGE = """
            INSERT INTO un_flow_urge
              (system_id,tenant_id,urge_id,instance_id,actor_id,recipient_id,message,created_at)
            SELECT ?,?,?,instance_id,?,?,?,?
            FROM un_flow_instance
            WHERE system_id=? AND tenant_id=? AND instance_id=?
              AND status='PENDING' AND requester_id=? AND approver_id=?
            """;

    public static final String SELECT_URGES = """
            SELECT urge_id,instance_id,actor_id,recipient_id,message,created_at
            FROM un_flow_urge
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            ORDER BY created_at ASC,urge_id ASC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_URGES = """
            SELECT COUNT(*)
            FROM un_flow_urge
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            """;

    public static final String INSERT_COMMENT = """
            INSERT INTO un_flow_comment
              (system_id,tenant_id,comment_id,instance_id,author_id,body,created_at)
            VALUES (?,?,?,?,?,?,?)
            """;

    public static final String SELECT_COMMENTS = """
            SELECT comment_id,instance_id,author_id,body,created_at
            FROM un_flow_comment
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            ORDER BY created_at ASC,comment_id ASC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_COMMENTS = """
            SELECT COUNT(*)
            FROM un_flow_comment
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            """;

    public static final String INSERT_COPY = """
            INSERT INTO un_flow_copy_recipient
              (system_id,tenant_id,copy_id,instance_id,actor_id,recipient_id,message,created_at)
            VALUES (?,?,?,?,?,?,?,?)
            """;

    public static final String SELECT_COPIES = """
            SELECT copy_id,instance_id,actor_id,recipient_id,message,created_at
            FROM un_flow_copy_recipient
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            ORDER BY created_at ASC,copy_id ASC
            LIMIT ? OFFSET ?
            """;

    public static final String COUNT_COPIES = """
            SELECT COUNT(*)
            FROM un_flow_copy_recipient
            WHERE system_id=? AND tenant_id=? AND instance_id=?
            """;

    private JdbcFlowInteractionSql() {
    }
}
