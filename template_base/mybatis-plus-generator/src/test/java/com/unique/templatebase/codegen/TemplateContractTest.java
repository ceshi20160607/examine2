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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模拟一张含敏感字段的业务表，核对五方法模板能生成并编译。 */
class TemplateContractTest {
    @TempDir
    Path temporary;

    /** 模板本身必须完整生成基础 CRUD，而不是空 IService/ServiceImpl。 */
    @Test
    void generatedServiceCompilesWithFiveMethodsAndSafeInformation() throws Exception {
        String service = render("service.java.ftl");
        String implementation = render("serviceImpl.java.ftl");
        for (String method : List.of("queryPageList", "addOrUpdate", "queryById", "information", "deleteByIds")) {
            assertTrue(service.contains(method + "("), "接口缺少 " + method);
            assertTrue(implementation.contains(method + "("), "实现缺少 " + method);
        }
        assertTrue(implementation.contains("values.put(\"name\""));
        assertFalse(implementation.contains("values.put(\"passwordHash\""), "敏感字段不进入基础详情");

        Path source = temporary.resolve("src");
        write(source, "com/unique/unexamine/print/base/entity/UnPrint.java", """
                package com.unique.unexamine.print.base.entity;
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
        write(source, "com/unique/unexamine/print/base/mapper/UnPrintMapper.java", """
                package com.unique.unexamine.print.base.mapper;
                import com.baomidou.mybatisplus.core.mapper.BaseMapper;
                import com.unique.unexamine.print.base.entity.UnPrint;
                public interface UnPrintMapper extends BaseMapper<UnPrint> { }
                """);
        write(source, "com/unique/unexamine/print/base/service/IUnPrintService.java", service);
        write(source, "com/unique/unexamine/print/base/service/impl/UnPrintServiceImpl.java", implementation);
        write(source, "com/unique/unexamine/print/base/service/impl/FixtureService.java", """
                package com.unique.unexamine.print.base.service.impl;
                import com.unique.unexamine.print.base.entity.UnPrint;
                public class FixtureService extends UnPrintServiceImpl {
                    @Override
                    public UnPrint queryById(Long id) { return new UnPrint(id, "示例"); }
                }
                """);

        compile(source);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{temporary.toUri().toURL()},
                getClass().getClassLoader())) {
            String servicePackage = "com.unique.unexamine.print.base.service.IUnPrintService";
            Class<?> layoutClass = loader.loadClass(servicePackage + "$FieldLayout");
            var layoutConstructor = layoutClass.getDeclaredConstructor(
                    String.class, String.class, int.class, int.class);
            List<Object> layout = List.of(
                    layoutConstructor.newInstance("name", "名称", 50, 1),
                    layoutConstructor.newInstance("id", "主键", 50, 2),
                    layoutConstructor.newInstance("customField", "自定义字段", 100, 3));
            Object serviceInstance = loader.loadClass(
                    "com.unique.unexamine.print.base.service.impl.FixtureService")
                    .getDeclaredConstructor().newInstance();
            var information = serviceInstance.getClass().getMethod(
                    "information", Long.class, List.class, Map.class);
            @SuppressWarnings("unchecked")
            List<Object> views = (List<Object>) information.invoke(serviceInstance, 1L,
                    layout, Map.of("customField", "自定义值"));
            assertEquals(3, views.size());
            var row = views.get(0).getClass().getMethod("row");
            var left = views.get(0).getClass().getMethod("leftPercent");
            var value = views.get(0).getClass().getMethod("value");
            assertEquals(1, row.invoke(views.get(0)));
            assertEquals(1, row.invoke(views.get(1)));
            assertEquals(2, row.invoke(views.get(2)));
            assertEquals(0, left.invoke(views.get(0)));
            assertEquals(50, left.invoke(views.get(1)));
            assertEquals(0, left.invoke(views.get(2)));
            assertEquals("自定义值", value.invoke(views.get(2)));
        }
    }

    /** 用独立内存样表运行真正的 MyBatis-Plus 生成流程，验证模板接线和输出编译。 */
    @Test
    void generatorProducesCompilableFiveMethodService() throws Exception {
        String url = "jdbc:h2:mem:codegen_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE un_print (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                    + "name VARCHAR(120) NOT NULL, password_hash VARCHAR(120))");
            statement.execute("COMMENT ON TABLE un_print IS '打印模板'");
            statement.execute("COMMENT ON COLUMN un_print.id IS '打印模板主键'");
            statement.execute("COMMENT ON COLUMN un_print.name IS '模板名称'");
            statement.execute("COMMENT ON COLUMN un_print.password_hash IS '口令摘要'");
        }
        Path source = temporary.resolve("actual");
        FastAutoGenerator.create(url, "sa", "")
                .globalConfig(builder -> builder.author("Template Base").disableOpenDir()
                        .outputDir(source.toString()))
                .packageConfig(builder -> builder.parent("com.unique.unexamine.print.base")
                        .entity("entity").mapper("mapper").service("service").serviceImpl("service.impl"))
                .strategyConfig(builder -> {
                    builder.addInclude("un_print");
                    builder.entityBuilder().enableTableFieldAnnotation();
                    builder.mapperBuilder().enableMapperAnnotation().disableMapperXml();
                    builder.serviceBuilder().formatServiceFileName("I%sService")
                            .formatServiceImplFileName("%sServiceImpl");
                    builder.controllerBuilder().disable();
                })
                .templateConfig(builder -> builder.service("/templates/service.java")
                        .serviceImpl("/templates/serviceImpl.java"))
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
        Path service = source.resolve("com/unique/unexamine/print/base/service/IUnPrintService.java");
        Path implementation = source.resolve("com/unique/unexamine/print/base/service/impl/UnPrintServiceImpl.java");
        assertTrue(Files.isRegularFile(service), "生成器未输出 Service 接口");
        assertTrue(Files.isRegularFile(implementation), "生成器未输出 ServiceImpl");
        for (String method : List.of("queryPageList", "addOrUpdate", "queryById", "information", "deleteByIds")) {
            assertTrue(Files.readString(implementation).contains(method + "("), "生成实现缺少 " + method);
        }
        write(source, "com/unique/unexamine/print/base/service/impl/FixtureService.java", """
                package com.unique.unexamine.print.base.service.impl;
                import com.unique.unexamine.print.base.mapper.UnPrintMapper;
                public class FixtureService extends UnPrintServiceImpl {
                    public void useMapper(UnPrintMapper mapper) { this.baseMapper = mapper; }
                }
                """);
        compile(source);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{temporary.toUri().toURL()},
                getClass().getClassLoader())) {
            Class<?> entityClass = loader.loadClass("com.unique.unexamine.print.base.entity.UnPrint");
            Class<?> mapperClass = loader.loadClass("com.unique.unexamine.print.base.mapper.UnPrintMapper");
            Class<?> pageQueryClass = loader.loadClass(
                    "com.unique.unexamine.print.base.service.IUnPrintService$PageQuery");
            MybatisConfiguration configuration = new MybatisConfiguration();
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
                        "com.unique.unexamine.print.base.service.impl.FixtureService")
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
                Object query = pageQueryClass.getDeclaredConstructor(long.class, long.class,
                        Map.class, String.class, boolean.class)
                        .newInstance(1L, 1L, Map.of("name", "模板一"), "id", false);
                Page<?> page = (Page<?>) fixture.getClass().getMethod("queryPageList", pageQueryClass)
                        .invoke(fixture, query);
                assertEquals(1, page.getRecords().size());
                assertEquals(2, page.getTotal());
                assertEquals("模板一", entityClass.getMethod("getName").invoke(
                        fixture.getClass().getMethod("queryById", Long.class).invoke(fixture, id)));
                entityClass.getMethod("setName", String.class).invoke(entity, "模板二");
                fixture.getClass().getMethod("addOrUpdate", entityClass).invoke(fixture, entity);
                assertEquals("模板二", entityClass.getMethod("getName").invoke(
                        fixture.getClass().getMethod("queryById", Long.class).invoke(fixture, id)));
                assertEquals(true, fixture.getClass().getMethod("deleteByIds", List.class)
                        .invoke(fixture, List.of(id)));
                assertEquals(0, pageQueryResultSize(fixture, pageQueryClass));
            }
        }
    }

    /** 删除后再次按同一筛选条件查询，应已无持久化记录。 */
    private static int pageQueryResultSize(Object fixture, Class<?> pageQueryClass) throws Exception {
        Object query = pageQueryClass.getDeclaredConstructor(long.class, long.class,
                Map.class, String.class, boolean.class)
                .newInstance(1L, 10L, Map.of("name", "模板二"), "id", false);
        Page<?> page = (Page<?>) fixture.getClass().getMethod("queryPageList", pageQueryClass)
                .invoke(fixture, query);
        return page.getRecords().size();
    }

    private void compile(Path source) throws IOException {
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
                "Service", "com.unique.unexamine.print.base.service",
                "ServiceImpl", "com.unique.unexamine.print.base.service.impl",
                "Entity", "com.unique.unexamine.print.base.entity",
                "Mapper", "com.unique.unexamine.print.base.mapper");
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
