package com.unique.examine.generator.engine;

import com.baomidou.mybatisplus.generator.config.ConstVal;
import com.baomidou.mybatisplus.generator.config.builder.ConfigBuilder;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.engine.AbstractTemplateEngine;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * examine base 层 Freemarker 模板引擎。
 *
 * <p>该引擎参考旧项目 `DefaultTemplateEngine`，负责修正模板变量并以 UTF-8 写出文件。</p>
 */
public class UniqueFreemarkerTemplateEngine extends AbstractTemplateEngine {

    private Configuration configuration;

    @Override
    public UniqueFreemarkerTemplateEngine init(ConfigBuilder configBuilder) {
        configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        configuration.setDefaultEncoding(ConstVal.UTF8);
        configuration.setClassForTemplateLoading(UniqueFreemarkerTemplateEngine.class, "/");
        return this;
    }

    @Override
    public String writer(Map<String, Object> objectMap, String templatePath, String outputFile) throws Exception {
        File file = new File(outputFile);
        writer(objectMap, templatePath, file);
        return file.getPath();
    }

    @Override
    public void writer(Map<String, Object> objectMap, String templatePath, File outputFile) throws Exception {
        Template template = configuration.getTemplate(templatePath);
        File parentFile = outputFile.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory: " + parentFile);
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {
            template.process(objectMap, writer);
        }
    }

    /**
     * 渲染模板文本，供单元测试或后续预览命令复用。
     *
     * @param objectMap 模板上下文
     * @param templatePath 模板路径
     * @return 渲染后的文本
     * @throws Exception 模板渲染失败时抛出
     */
    public String render(Map<String, Object> objectMap, String templatePath) throws Exception {
        Template template = configuration.getTemplate(templatePath);
        try (StringWriter writer = new StringWriter()) {
            template.process(objectMap, writer);
            return writer.toString();
        }
    }

    @Override
    public Map<String, Object> getObjectMap(ConfigBuilder config, TableInfo tableInfo) {
        Map<String, Object> objectMap = super.getObjectMap(config, tableInfo);
        // 兼容旧模板中对 serviceName、mapperName、entityName 小驼峰变量的使用。
        Object controllerMappingHyphenStyle = objectMap.get("controllerMappingHyphenStyle");
        Object controllerMappingHyphen = objectMap.get("controllerMappingHyphen");
        if (Boolean.FALSE.equals(controllerMappingHyphenStyle) && controllerMappingHyphen != null) {
            objectMap.put("controllerMappingHyphen", toCamelCase(controllerMappingHyphen.toString().replace('-', '_')));
        }
        objectMap.put("serviceName", lowerFirst(removeServicePrefix(tableInfo.getServiceName())));
        objectMap.put("mapperName", lowerFirst(tableInfo.getMapperName()));
        objectMap.put("entityName", lowerFirst(tableInfo.getEntityName()));
        return objectMap;
    }

    @Override
    public String templateFilePath(String filePath) {
        return filePath + ".ftl";
    }

    private static String removeServicePrefix(String serviceName) {
        if (serviceName != null && serviceName.length() > 1 && serviceName.charAt(0) == 'I'
                && Character.isUpperCase(serviceName.charAt(1))) {
            return serviceName.substring(1);
        }
        return serviceName;
    }

    private static String lowerFirst(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }

    private static String toCamelCase(String text) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char current : text.toCharArray()) {
            if (current == '_') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                builder.append(Character.toUpperCase(current));
                upperNext = false;
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }
}
