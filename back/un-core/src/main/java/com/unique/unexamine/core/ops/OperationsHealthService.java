package com.unique.unexamine.core.ops;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/** 运维体检只读取实际基础设施，不返回口令、连接串或文件绝对路径。 */
@Service
public class OperationsHealthService {
    public record Check(String status,String detail) {}
    /** 当前运行进程的部署信息；不包含主机地址、存储路径和敏感配置值。 */
    public record Deployment(LocalDateTime startTime,List<String> profiles,String javaVersion,String port) {}
    public record Snapshot(LocalDateTime inspectedTime,String serviceVersion,
                           Deployment deployment,
                           Map<String,Check> components,Map<String,Boolean> secretConfiguration,
                           java.util.List<RecentErrorRegistry.ErrorView> recentErrors) {}

    private final DataSource dataSource;
    private final RedisConnectionFactory redis;
    private final Environment environment;
    private final RecentErrorRegistry recentErrors;

    public OperationsHealthService(DataSource dataSource,RedisConnectionFactory redis,Environment environment,
                                   RecentErrorRegistry recentErrors) {
        this.dataSource=dataSource; this.redis=redis; this.environment=environment; this.recentErrors=recentErrors;
    }

    /** 数据库、Redis 和本地文件目录分别探测，单项失败仍返回其他项的结果。 */
    public Snapshot inspect() {
        Map<String,Check> components=new LinkedHashMap<>();
        try (Connection connection=dataSource.getConnection()) {
            components.put("database",connection.isValid(2)?new Check("UP","连接有效"):new Check("DOWN","连接无效"));
        } catch (Exception ex) { components.put("database",new Check("DOWN",ex.getClass().getSimpleName())); }
        try (var connection=redis.getConnection()) {
            components.put("redis","PONG".equalsIgnoreCase(connection.ping())
                    ?new Check("UP","响应正常"):new Check("DOWN","无有效响应"));
        } catch (Exception ex) { components.put("redis",new Check("DOWN",ex.getClass().getSimpleName())); }
        Path root=Path.of(environment.getProperty("un.file.local.root","data/files")).toAbsolutePath().normalize();
        Path check=Files.exists(root)?root:root.getParent();
        boolean accessible=check!=null && Files.isDirectory(check) && Files.isWritable(check);
        components.put("fileStorage",new Check(accessible?"UP":"DOWN",
                accessible?Files.exists(root)?"目录可写":"父目录可写，上传时创建存储目录":"存储目录不可写"));
        Map<String,Boolean> secrets=Map.of("databasePassword",configured("APP_DB_PASSWORD"),
                "redisPassword",configured("APP_REDIS_PASSWORD"),
                "initialAdminPassword",configured("APP_INITIAL_ADMIN_PASSWORD"));
        LocalDateTime startTime=LocalDateTime.ofInstant(
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()),ZoneId.systemDefault());
        Deployment deployment=new Deployment(startTime,List.of(environment.getActiveProfiles()),
                System.getProperty("java.version"),environment.getProperty("server.port","9999"));
        return new Snapshot(LocalDateTime.now(),version(),deployment,Map.copyOf(components),secrets,recentErrors.latest());
    }

    private boolean configured(String key) {
        String value=environment.getProperty(key);
        return value!=null && !value.isBlank();
    }

    private String version() {
        try (InputStream stream=getClass().getResourceAsStream("/service-version.properties")) {
            if (stream==null) return "UNKNOWN";
            var properties=new java.util.Properties(); properties.load(stream);
            return properties.getProperty("service.version","UNKNOWN");
        } catch (Exception ex) { return "UNKNOWN"; }
    }
}
