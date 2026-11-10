package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.ops.OperationsSqlFileService;
import com.unique.unexamine.plat.base.entity.UnPlatOpsBackup;
import com.unique.unexamine.plat.base.service.IUnPlatOpsBackupService;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.manage.dto.SystemBackupDto.View;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

/** 按当前系统归属导出全部配置和业务运行数据，并附上这些表的当前建表语句。 */
@Service
public class SystemSqlBackupService {
    /** 没有 system_id 的子表必须沿父表逐级限定；漏配新表时停止导出，不能悄悄漏数据。 */
    private static final Map<String,String> CHILD=Map.ofEntries(
            Map.entry("un_app_event","application_id IN (SELECT id FROM un_app WHERE system_id=?)"),
            Map.entry("un_dashboard_component","dashboard_id IN (SELECT id FROM un_dashboard WHERE system_id=?)"),
            Map.entry("un_dashboard_component_field","component_id IN (SELECT id FROM un_dashboard_component WHERE dashboard_id IN (SELECT id FROM un_dashboard WHERE system_id=?))"),
            Map.entry("un_dashboard_user","dashboard_id IN (SELECT id FROM un_dashboard WHERE system_id=?)"),
            Map.entry("un_data_source_config","data_source_id IN (SELECT id FROM un_data_source WHERE system_id=?)"),
            Map.entry("un_examine_config","examine_id IN (SELECT id FROM un_examine WHERE system_id=?)"),
            Map.entry("un_examine_config_user","config_id IN (SELECT id FROM un_examine_config WHERE examine_id IN (SELECT id FROM un_examine WHERE system_id=?))"),
            Map.entry("un_examine_example_log","example_id IN (SELECT id FROM un_examine_example WHERE system_id=?)"),
            Map.entry("un_examine_example_node","example_id IN (SELECT id FROM un_examine_example WHERE system_id=?)"),
            Map.entry("un_examine_example_node_user","example_node_id IN (SELECT id FROM un_examine_example_node WHERE example_id IN (SELECT id FROM un_examine_example WHERE system_id=?))"),
            Map.entry("un_examine_exception","example_id IN (SELECT id FROM un_examine_example WHERE system_id=?)"),
            Map.entry("un_examine_node","examine_id IN (SELECT id FROM un_examine WHERE system_id=?)"),
            Map.entry("un_examine_node_field","node_id IN (SELECT id FROM un_examine_node WHERE examine_id IN (SELECT id FROM un_examine WHERE system_id=?))"),
            Map.entry("un_examine_node_user","node_id IN (SELECT id FROM un_examine_node WHERE examine_id IN (SELECT id FROM un_examine WHERE system_id=?))"),
            Map.entry("un_flow_action","example_id IN (SELECT id FROM un_flow_example WHERE system_id=?)"),
            Map.entry("un_flow_edge","flow_id IN (SELECT id FROM un_flow WHERE system_id=?)"),
            Map.entry("un_flow_event","example_id IN (SELECT id FROM un_flow_example WHERE system_id=?)"),
            Map.entry("un_flow_exception","example_id IN (SELECT id FROM un_flow_example WHERE system_id=?)"),
            Map.entry("un_flow_job","example_id IN (SELECT id FROM un_flow_example WHERE system_id=?)"),
            Map.entry("un_flow_node","flow_id IN (SELECT id FROM un_flow WHERE system_id=?)"),
            Map.entry("un_flow_task","example_id IN (SELECT id FROM un_flow_example WHERE system_id=?)"),
            Map.entry("un_flow_task_candidate","task_id IN (SELECT id FROM un_flow_task WHERE example_id IN (SELECT id FROM un_flow_example WHERE system_id=?))"),
            Map.entry("un_flow_version","flow_id IN (SELECT id FROM un_flow WHERE system_id=?)"),
            Map.entry("un_kpi_result","kpi_id IN (SELECT id FROM un_kpi WHERE system_id=?)"),
            Map.entry("un_kpi_user","kpi_id IN (SELECT id FROM un_kpi WHERE system_id=?)"),
            Map.entry("un_module_action","module_id IN (SELECT id FROM un_module WHERE system_id=?)"),
            Map.entry("un_module_exchange_row","job_id IN (SELECT id FROM un_module_exchange_job WHERE system_id=?)"),
            Map.entry("un_module_field_sort","module_id IN (SELECT id FROM un_module WHERE system_id=?)"),
            Map.entry("un_module_page_field","page_id IN (SELECT id FROM un_module_page WHERE system_id=?)"),
            Map.entry("un_module_query_field","module_id IN (SELECT id FROM un_module WHERE system_id=?)"),
            Map.entry("un_module_record_data","record_id IN (SELECT id FROM un_module_record WHERE system_id=?)"),
            Map.entry("un_module_relation","source_record_id IN (SELECT id FROM un_module_record WHERE system_id=?) AND target_record_id IN (SELECT id FROM un_module_record WHERE system_id=?)"),
            Map.entry("un_module_rule","module_id IN (SELECT id FROM un_module WHERE system_id=?)"),
            Map.entry("un_module_scence","module_id IN (SELECT id FROM un_module WHERE system_id=?)"),
            Map.entry("un_module_scence_config","scence_id IN (SELECT id FROM un_module_scence WHERE module_id IN (SELECT id FROM un_module WHERE system_id=?))"),
            Map.entry("un_module_sequence","module_id IN (SELECT id FROM un_module WHERE system_id=?)"),
            Map.entry("un_module_teamer","record_id IN (SELECT id FROM un_module_record WHERE system_id=?)"),
            Map.entry("un_module_tenant_field","module_id IN (SELECT id FROM un_module WHERE system_id=?)"),
            Map.entry("un_sys_member_role","tenant_member_id IN (SELECT id FROM un_sys_tenant_member WHERE system_id=?)"),
            Map.entry("un_sys_role_data","role_id IN (SELECT id FROM un_sys_role WHERE system_id=?)"),
            Map.entry("un_sys_role_field","role_id IN (SELECT id FROM un_sys_role WHERE system_id=?)"),
            Map.entry("un_sys_role_menu","role_id IN (SELECT id FROM un_sys_role WHERE system_id=?)"),
            Map.entry("un_work_daily_comment","daily_root_id IN (SELECT root_id FROM un_work_daily WHERE system_id=?)"),
            Map.entry("un_work_project_member","project_id IN (SELECT id FROM un_work_project WHERE system_id=?)"),
            Map.entry("un_work_task_group","project_id IN (SELECT id FROM un_work_project WHERE system_id=?)"),
            Map.entry("un_work_task_history","task_id IN (SELECT id FROM un_work_task WHERE system_id=?)"),
            Map.entry("un_work_task_member","task_id IN (SELECT id FROM un_work_task WHERE system_id=?)"));

    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnPlatOpsBackupService backups;
    private final IUnSysLogOptService logs;
    private final OperationsSqlFileService files;
    private final DataSource dataSource;

    public SystemSqlBackupService(SystemManageService systems,IUnSysSystemService systemRows,
            IUnPlatOpsBackupService backups,IUnSysLogOptService logs,OperationsSqlFileService files,DataSource dataSource) {
        this.systems=systems; this.systemRows=systemRows; this.backups=backups; this.logs=logs;
        this.files=files; this.dataSource=dataSource;
    }

    public Page<View> list(String token,Long systemId,Long tenantId,SearchBO search) {
        owner(token,systemId,tenantId);
        SearchBO criteria=search==null?new SearchBO():search;
        Page<UnPlatOpsBackup> page=backups.page(criteria.parse(),new LambdaQueryWrapper<UnPlatOpsBackup>()
                .eq(UnPlatOpsBackup::getScopeType,"SYSTEM").eq(UnPlatOpsBackup::getSystemId,systemId)
                .orderByDesc(UnPlatOpsBackup::getId));
        return new Page<View>(page.getCurrent(),page.getSize(),page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 一致性读事务导出所属系统的表结构和数据，失败时记录状态但不发布半成品。 */
    public View create(String token,Long systemId,Long tenantId) {
        Long actor=owner(token,systemId,tenantId);
        UnPlatOpsBackup row=new UnPlatOpsBackup(); row.setScopeType("SYSTEM"); row.setSystemId(systemId);
        row.setBackupKind("SYSTEM_SQL"); row.setStatus("CREATING"); row.setCreateAccountId(actor);
        row.setCreateTime(LocalDateTime.now()); backups.addOrUpdate(row);
        audit(systemId,tenantId,actor,"SYSTEM_BACKUP_START",row.getId(),"系统 SQL 导出开始");
        try {
            var stored=files.create(output -> export(output,systemId));
            row.setArtifactKey(stored.key()); row.setSizeBytes(stored.sizeBytes()); row.setSha256(stored.sha256());
            row.setStatus("READY"); backups.addOrUpdate(row);
            audit(systemId,tenantId,actor,"SYSTEM_BACKUP_READY",row.getId(),"系统 SQL 可下载");
            return view(row);
        } catch (RuntimeException ex) {
            row.setStatus("FAILED"); row.setErrorText("系统数据导出或文件存储失败"); backups.addOrUpdate(row);
            audit(systemId,tenantId,actor,"SYSTEM_BACKUP_FAILED",row.getId(),"系统 SQL 导出失败");
            throw ex;
        }
    }

    /** 下载前再次核对系统所属人、备份归属和文件摘要。 */
    public Path download(String token,Long systemId,Long tenantId,Long backupId) {
        Long actor=owner(token,systemId,tenantId);
        UnPlatOpsBackup row=backups.queryById(backupId);
        if (row==null || !"SYSTEM".equals(row.getScopeType()) || !systemId.equals(row.getSystemId())
                || !"SYSTEM_SQL".equals(row.getBackupKind()) || !"READY".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"可用的系统 SQL 备份不存在");
        Path file=files.verified(row.getArtifactKey(),row.getSha256());
        audit(systemId,tenantId,actor,"SYSTEM_BACKUP_DOWNLOAD",backupId,"下载系统 SQL");
        return file;
    }

    /** 所有查询使用同一事务快照；任何不明归属的表都使导出失败。 */
    private void export(OutputStream output,Long systemId) throws Exception {
        try (Connection connection=dataSource.getConnection();
             BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8))) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true); connection.setAutoCommit(false);
            writer.write("-- 业务系统 "+systemId+" 的 SQL 备份；平台账号及其他系统数据不在本文件中。\n");
            writer.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n\n");
            for (String table:tables(connection)) {
                String condition=scope(connection,table);
                writer.write("-- "+table+" 表结构及系统 "+systemId+" 的数据\n");
                try (PreparedStatement statement=connection.prepareStatement("SHOW CREATE TABLE `"+table+"`");
                     ResultSet result=statement.executeQuery()) {
                    if (!result.next()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"备份表结构不存在: "+table);
                    String ddl=result.getString(2).replaceFirst("^CREATE TABLE ","CREATE TABLE IF NOT EXISTS ");
                    writer.write(ddl); writer.write(";\n");
                }
                try (PreparedStatement statement=connection.prepareStatement(
                        "SELECT * FROM `"+table+"` WHERE "+condition,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
                    for (int i=1;i<=condition.chars().filter(ch -> ch=='?').count();i++) statement.setLong(i,systemId);
                    statement.setFetchSize(Integer.MIN_VALUE);
                    try (ResultSet rows=statement.executeQuery()) {
                        ResultSetMetaData columns=rows.getMetaData();
                        while (rows.next()) {
                            writer.write("INSERT INTO `"+table+"` (");
                            for (int i=1;i<=columns.getColumnCount();i++) {
                                if (i>1) writer.write(',');
                                writer.write('`'); writer.write(columns.getColumnLabel(i)); writer.write('`');
                            }
                            writer.write(") VALUES (");
                            for (int i=1;i<=columns.getColumnCount();i++) {
                                if (i>1) writer.write(',');
                                writer.write(value(rows,columns,i));
                            }
                            writer.write(");\n");
                        }
                    }
                }
                writer.write('\n');
            }
            writer.write("SET FOREIGN_KEY_CHECKS=1;\n"); writer.flush();
            connection.commit();
        }
    }

    private List<String> tables(Connection connection) throws Exception {
        List<String> result=new ArrayList<>();
        try (PreparedStatement statement=connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name");
             ResultSet rows=statement.executeQuery()) {
            while (rows.next()) {
                String table=rows.getString(1);
                if (!table.matches("[a-z0-9_]+")) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"备份表名无效");
                if (!table.startsWith("un_plat_")) result.add(table);
            }
        }
        if (!result.contains("un_sys_system") || !result.contains("un_module_record"))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"系统备份缺少基础业务表");
        return result;
    }

    private String scope(Connection connection,String table) throws Exception {
        if ("un_sys_system".equals(table)) return "id=?";
        try (PreparedStatement query=connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name='system_id'")) {
            query.setString(1,table);
            try (ResultSet result=query.executeQuery()) {
                result.next(); if (result.getInt(1)>0) return "system_id=?";
            }
        }
        String condition=CHILD.get(table);
        if (condition==null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"未配置表的系统归属: "+table);
        return condition;
    }

    /** 文本用 UTF-8 十六进制表达，数字和时间保留数据库原值，避免引号和换行破坏 SQL。 */
    private String value(ResultSet row,ResultSetMetaData columns,int index) throws Exception {
        Object raw=row.getObject(index);
        if (raw==null) return "NULL";
        int type=columns.getColumnType(index);
        if (type==Types.BOOLEAN || type==Types.BIT) return row.getBoolean(index)?"1":"0";
        if (type==Types.TINYINT || type==Types.SMALLINT || type==Types.INTEGER || type==Types.BIGINT
                || type==Types.FLOAT || type==Types.REAL || type==Types.DOUBLE
                || type==Types.NUMERIC || type==Types.DECIMAL) return row.getString(index);
        if (type==Types.DATE || type==Types.TIME || type==Types.TIMESTAMP || type==Types.TIMESTAMP_WITH_TIMEZONE)
            return "'"+row.getString(index).replace("'","''")+"'";
        byte[] bytes=row.getBytes(index);
        String hex=java.util.HexFormat.of().formatHex(bytes);
        if (type==Types.BINARY || type==Types.VARBINARY || type==Types.LONGVARBINARY || type==Types.BLOB)
            return "0x"+hex;
        return "CONVERT(0x"+hex+" USING utf8mb4)";
    }

    private Long owner(String token,Long systemId,Long tenantId) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        var system=systemRows.queryById(systemId);
        Long actor=systems.accountId(token,systemId);
        if (system==null || !context.tenantId().equals(system.getRootTenantId())
                || !actor.equals(system.getOwnerAccountId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"只有系统所属人可备份全系统数据");
        return actor;
    }

    private View view(UnPlatOpsBackup row) {
        return new View(row.getId(),row.getStatus(),row.getSizeBytes(),row.getSha256(),row.getErrorText(),row.getCreateTime());
    }

    private void audit(Long systemId,Long tenantId,Long actor,String action,Long backupId,String summary) {
        UnSysLogOpt log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(tenantId);
        log.setAccountId(actor); log.setActionCode(action); log.setObjectType("OPS_BACKUP"); log.setObjectId(backupId);
        log.setDetailJson("{\"summary\":\""+summary+"\",\"systemId\":"+systemId+"}");
        logs.addOrUpdate(log);
    }
}
