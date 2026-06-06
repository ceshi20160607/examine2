package com.kakarote.generator.engine;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.generator.config.ConstVal;
import com.baomidou.mybatisplus.generator.config.builder.ConfigBuilder;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.engine.AbstractTemplateEngine;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;

public class DefaultTemplateEngine extends AbstractTemplateEngine {
    private Configuration configuration;

    @Override
    public DefaultTemplateEngine init(ConfigBuilder configBuilder) {
        configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        configuration.setDefaultEncoding(ConstVal.UTF8);
        configuration.setClassForTemplateLoading(FreemarkerTemplateEngine.class, StringPool.SLASH);
        return this;
    }


    @Override
    public void writer(Map<String, Object> objectMap, String templatePath, File outputFile) throws Exception {
        Template template = configuration.getTemplate(templatePath);
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            template.process(objectMap, new OutputStreamWriter(fileOutputStream, ConstVal.UTF8));
        }
    }

    @Override
    public Map<String, Object> getObjectMap(ConfigBuilder config, TableInfo tableInfo) {
        Map<String, Object> objectMap = super.getObjectMap(config, tableInfo);
        //目前版本mybatis-plus代码生成器有bug，默认按照controllerMappingHyphenStyle为true的数据
        if (Boolean.FALSE.equals(objectMap.get("controllerMappingHyphenStyle"))) {
            String controllerMappingHyphen = objectMap.get("controllerMappingHyphen").toString();
            objectMap.put("controllerMappingHyphen", StrUtil.toCamelCase(controllerMappingHyphen.replace('-', '_')));
        }
        objectMap.put("serviceName", CharSequenceUtil.lowerFirst(tableInfo.getServiceName().substring(1)));
        objectMap.put("mapperName", CharSequenceUtil.lowerFirst(tableInfo.getMapperName()));
        objectMap.put("entityName", CharSequenceUtil.lowerFirst(tableInfo.getEntityName()));

        return objectMap;
    }

    @Override
    public String templateFilePath(String filePath) {
        return filePath + ".ftl";
    }
}
