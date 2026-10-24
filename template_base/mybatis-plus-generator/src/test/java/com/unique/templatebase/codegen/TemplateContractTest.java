package com.unique.templatebase.codegen;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import freemarker.template.Configuration;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** 模拟一张含敏感字段的业务表，核对基础 CRUD模板能生成并编译。 */
class TemplateContractTest {
    @TempDir
    Path temporary;

    /** 即使本批目标表已存在，其他固定业务表缺失时也不能启动局部生成。 */
    @Test
    void generatorRequiresWholeSchemaBeforeBatchGeneration() throws Exception {
        String url = "jdbc:h2:mem:schema_gate_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE un_print (id BIGINT PRIMARY KEY)");
            Set<String> required = Set.of("un_print", "un_work_task");
            var failure = assertThrows(IllegalStateException.class,
                    () -> BaseCodeGenerator.verifyCompleteSchema(connection, required));
            assertTrue(failure.getMessage().contains("un_work_task"));
            statement.execute("CREATE TABLE un_work_task (id BIGINT PRIMARY KEY)");
            assertDoesNotThrow(() -> BaseCodeGenerator.verifyCompleteSchema(connection, required));
        }
    }

    /** 基础方法可生成编译，字段布局和业务筛选不进入生成结果。 */
    @Test
    void generatedServiceCompilesWithBasicMethods() throws Exception {
        String service = render("service.java.ftl");
        String implementation = render("serviceImpl.java.ftl");
        for (String method : List.of("queryPageList", "addOrUpdate", "queryById", "deleteByIds")) {
            assertTrue(service.contains(method + "("), "接口缺少 " + method);
            assertTrue(implementation.contains(method + "("), "实现缺少 " + method);
        }
        assertTrue(service.contains("queryPageList(SearchBO search)"), "基础分页须接收可扩展的查询对象");
        assertTrue(implementation.contains("search.parse()"), "基础分页须复用公共分页解析");
        assertFalse(implementation.contains("case \"name\""), "基础 CRUD 不枚举字段");
        assertFalse(implementation.contains("information("), "表单绑定应由 manage 完成");

        Path source = temporary.resolve("src");
        write(source, "com/unique/unexamine/sys/action/print/base/entity/UnPrint.java", """
                package com.unique.unexamine.sys.action.print.base.entity;
                public class UnPrint {
                    private Long id;
                    private String name;
                    private String passwordHash;
                    public UnPrint() { }
                    public UnPrint(Long id, String name) { this.id = id; this.name = name; }
                    public Long getId() { return id; }
                    public String getName() { return name; }
                    public String getPasswordHash() { return passwordHash; }
                }
                """);
        write(source, "com/unique/unexamine/sys/action/print/base/mapper/UnPrintMapper.java", """
                package com.unique.unexamine.sys.action.print.base.mapper;
                import com.baomidou.mybatisplus.core.mapper.BaseMapper;
                import com.unique.unexamine.sys.action.print.base.entity.UnPrint;
                public interface UnPrintMapper extends BaseMapper<UnPrint> { }
                """);
        write(source, "com/unique/unexamine/sys/action/print/base/service/IUnPrintService.java", service);
        write(source, "com/unique/unexamine/sys/action/print/base/service/impl/UnPrintServiceImpl.java", implementation);
        compile(source);
    }

    /** 用独立内存样表运行真正的 MyBatis-Plus 生成流程，验证模板接线和输出编译。 */
    @Test
    void generatorProducesCompilableBasicMethodService() throws Exception {
        String url = "jdbc:h2:mem:codegen_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE un_print (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                    + "name VARCHAR(120) NOT NULL, remark VARCHAR(120), password_hash VARCHAR(120), deleted_time TIMESTAMP)");
            statement.execute("COMMENT ON TABLE un_print IS '打印模板'");
            statement.execute("COMMENT ON COLUMN un_print.id IS '打印模板主键'");
            statement.execute("COMMENT ON COLUMN un_print.name IS '模板名称'");
            statement.execute("COMMENT ON COLUMN un_print.remark IS '模板备注'");
            statement.execute("COMMENT ON COLUMN un_print.password_hash IS '口令摘要'");
            statement.execute("COMMENT ON COLUMN un_print.deleted_time IS '逻辑删除时间，空表示未删除'");
        }
        Path source = temporary.resolve("actual");
        FastAutoGenerator.create(url, "sa", "")
                .globalConfig(builder -> builder.author("Template Base").disableOpenDir()
                        .outputDir(source.toString()))
                .packageConfig(builder -> builder.parent("com.unique.unexamine.sys.action.print.base")
                        .entity("entity").mapper("mapper").service("service").serviceImpl("service.impl"))
                .strategyConfig(builder -> {
                    builder.addInclude("un_print");
                    builder.entityBuilder().enableTableFieldAnnotation().logicDeleteColumnName("deleted_time");
                    builder.mapperBuilder().enableMapperAnnotation().disableMapperXml();
                    builder.serviceBuilder().formatServiceFileName("I%sService")
                            .formatServiceImplFileName("%sServiceImpl");
                    builder.controllerBuilder().disable();
                })
                .templateConfig(builder -> builder.service("/templates/service.java")
                        .serviceImpl("/templates/serviceImpl.java"))
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
        Path service = source.resolve("com/unique/unexamine/sys/action/print/base/service/IUnPrintService.java");
        Path implementation = source.resolve("com/unique/unexamine/sys/action/print/base/service/impl/UnPrintServiceImpl.java");
        assertTrue(Files.isRegularFile(service), "生成器未输出 Service 接口");
        assertTrue(Files.isRegularFile(implementation), "生成器未输出 ServiceImpl");
        for (String method : List.of("queryPageList", "addOrUpdate", "queryById", "deleteByIds")) {
            assertTrue(Files.readString(implementation).contains(method + "("), "生成实现缺少 " + method);
        }
        write(source, "com/unique/unexamine/sys/action/print/base/service/impl/FixtureService.java", """
                package com.unique.unexamine.sys.action.print.base.service.impl;
                import com.unique.unexamine.sys.action.print.base.mapper.UnPrintMapper;
                public class FixtureService extends UnPrintServiceImpl {
                    public void useMapper(UnPrintMapper mapper) { this.baseMapper = mapper; }
                }
                """);
        compile(source);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{temporary.toUri().toURL()},
                getClass().getClassLoader())) {
            Class<?> entityClass = loader.loadClass("com.unique.unexamine.sys.action.print.base.entity.UnPrint");
            Class<?> mapperClass = loader.loadClass("com.unique.unexamine.sys.action.print.base.mapper.UnPrintMapper");
            Class<?> searchClass = loader.loadClass("com.unique.unexamine.core.bo.SearchBO");
            MybatisConfiguration configuration = new MybatisConfiguration();
            var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
            global.setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig()
                    .setLogicNotDeleteValue("null").setLogicDeleteValue("CURRENT_TIMESTAMP"));
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(configuration, global);
            configuration.addMapper(mapperClass);
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(new UnpooledDataSource("org.h2.Driver", url, "sa", ""));
            factoryBean.setConfiguration(configuration);
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
            factoryBean.setPlugins(interceptor);
            factoryBean.afterPropertiesSet();
            try (SqlSession session = factoryBean.getObject().openSession(true)) {
                Object fixture = loader.loadClass(
                        "com.unique.unexamine.sys.action.print.base.service.impl.FixtureService")
                        .getDeclaredConstructor().newInstance();
                fixture.getClass().getMethod("useMapper", mapperClass)
                        .invoke(fixture, session.getMapper(mapperClass));
                Object entity = entityClass.getDeclaredConstructor().newInstance();
                entityClass.getMethod("setName", String.class).invoke(entity, "模板一");
                fixture.getClass().getMethod("addOrUpdate", entityClass).invoke(fixture, entity);
                Long id = (Long) entityClass.getMethod("getId").invoke(entity);
                assertNotNull(id, "新增应返回真实主键");
                Object second = entityClass.getDeclaredConstructor().newInstance();
                entityClass.getMethod("setName", String.class).invoke(second, "模板一");
                fixture.getClass().getMethod("addOrUpdate", entityClass).invoke(fixture, second);
                Object search = searchClass.getDeclaredConstructor().newInstance();
                searchClass.getMethod("setLimit", Integer.class).invoke(search, 1);
                Page<?> page = (Page<?>) fixture.getClass().getMethod("queryPageList",searchClass)
                        .invoke(fixture,search);
                assertEquals(1,page.getRecords().size());
                assertEquals(2,page.getTotal());
                assertEquals("模板一", entityClass.getMethod("getName").invoke(
                        fixture.getClass().getMethod("queryById", Long.class).invoke(fixture, id)));
                entityClass.getMethod("setName", String.class).invoke(entity, "模板二");
                fixture.getClass().getMethod("addOrUpdate", entityClass).invoke(fixture, entity);
                assertEquals("模板二", entityClass.getMethod("getName").invoke(
                        fixture.getClass().getMethod("queryById", Long.class).invoke(fixture, id)));
                assertEquals(true, fixture.getClass().getMethod("deleteByIds", List.class)
                        .invoke(fixture, List.of(id)));
                searchClass.getMethod("setLimit", Integer.class).invoke(search, 10);
                assertEquals(1, ((Page<?>) fixture.getClass().getMethod("queryPageList",searchClass)
                        .invoke(fixture,search)).getRecords().size());
                // ORM 查询过滤已删除记录，但原行和删除时间必须真实留在数据库中。
                try (var connection = DriverManager.getConnection(url, "sa", "");
                     var statement = connection.prepareStatement("SELECT deleted_time FROM un_print WHERE id=?")) {
                    statement.setLong(1, id);
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next(), "逻辑删除不能物理清除原行");
                        assertNotNull(rows.getTimestamp(1), "删除必须写入删除时间");
                    }
                }
            }
        }
    }

    private void compile(Path source) throws IOException {
        write(source, "com/unique/unexamine/core/bo/SearchBO.java", """
                package com.unique.unexamine.core.bo;
                import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
                public class SearchBO {
                    private Integer page = 1;
                    private Integer limit = 15;
                    public void setLimit(Integer limit) { this.limit = limit; }
                    public <T> Page<T> parse() { return Page.of(page, limit); }
                }
                """);
        write(source, "com/unique/unexamine/core/enums/SystemCodeEnum.java", """
                package com.unique.unexamine.core.enums;
                public enum SystemCodeEnum { SYSTEM_NO_FOUND }
                """);
        write(source, "com/unique/unexamine/core/exception/BaseException.java", """
                package com.unique.unexamine.core.exception;
                import com.unique.unexamine.core.enums.SystemCodeEnum;
                public class BaseException extends RuntimeException {
                    public BaseException(SystemCodeEnum code, String message) { super(message); }
                }
                """);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "模板检查需要 JDK 21");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<Path> files = Files.walk(source).filter(path -> path.toString().endsWith(".java")).toList();
            var units = manager.getJavaFileObjectsFromPaths(files);
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Boolean success = compiler.getTask(null, manager, diagnostics,
                    List.of("-classpath", classpath, "-d", temporary.toString()), null, units).call();
            assertTrue(Boolean.TRUE.equals(success), diagnostics.getDiagnostics().toString());
        }
    }

    /** 使用生成器真实的 FTL 资源和与业务表一致的元数据字段渲染。 */
    private String render(String name) throws Exception {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setClassForTemplateLoading(BaseCodeGenerator.class, "/templates");
        Map<String, Object> packages = Map.of(
                "Service", "com.unique.unexamine.sys.action.print.base.service",
                "ServiceImpl", "com.unique.unexamine.sys.action.print.base.service.impl",
                "Entity", "com.unique.unexamine.sys.action.print.base.entity",
                "Mapper", "com.unique.unexamine.sys.action.print.base.mapper");
        Map<String, Object> table = Map.of(
                "comment", "打印模板", "serviceName", "IUnPrintService",
                "serviceImplName", "UnPrintServiceImpl", "mapperName", "UnPrintMapper",
                "fields", List.of(
                        field("id", "id", "Id", "打印模板主键", true),
                        field("name", "name", "Name", "模板名称", false),
                        field("passwordHash", "password_hash", "PasswordHash", "口令摘要", false)));
        StringWriter output = new StringWriter();
        configuration.getTemplate(name, "UTF-8")
                .process(Map.of("package", packages, "table", table, "entity", "UnPrint"), output);
        return output.toString();
    }

    /** 模拟 MyBatis-Plus 表字段元数据。 */
    private static Map<String, Object> field(String property, String column, String capital,
                                              String comment, boolean key) {
        return Map.of("propertyName", property, "name", column,
                "capitalName", capital, "comment", comment, "keyFlag", key);
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
