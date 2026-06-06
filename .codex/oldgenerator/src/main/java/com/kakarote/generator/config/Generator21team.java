package com.kakarote.generator.config;

import cn.hutool.core.date.DatePattern;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.enums.SqlLike;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.IFill;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.po.LikeTable;
import com.baomidou.mybatisplus.generator.config.querys.MySqlQuery;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.fill.Column;
import com.kakarote.generator.engine.DefaultTemplateEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;


/**
 * 默认的代码生成器配置
 *
 */
public class Generator21team {


    public static final String GENERATOR_URL = "jdbc:mysql://localhost:3306/aaaagenger";
    public static final String GENERATOR_USERNAME = "root";
    public static final String GENERATOR_PASSWORD = "password";
    public static final String GENERATOR_AUTHOR = "UNIQUE";
    public static final String GENERATOR_PACKAGE = "com.kakarote.crm.psi";
    public static final String GENERATOR_LOCAL_PATH = "D://generator";
    public static final String GENERATOR_LOCAL_XML_PATH = GENERATOR_LOCAL_PATH+"//xml";

    public static void main(String[] args) {
        System.out.println("请输入模块名称：");
        Scanner input = new Scanner(System.in);
        String modelName = input.next();
//        System.out.println("请输入作者名称：");
        String author = "UNIQUE";
        FastAutoGenerator
                //数据源配置
                .create(Generator21team.getDataSourceConfig())
                //全局配置
                .globalConfig(Generator21team.getGlobalConfig(author))
                //策略配置,传值为2种，如传值为模块名，如admin,则生成 wk_admin_* 所有表，如果传入的是一个具体表名，如wk_admin_user，则生成规则是生成单个表，模块名称取wk_下第二个单词，如wk_admin_user 取 admin
                .strategyConfig(Generator21team.getStrategyConfig(modelName))
                //包相关信息
                .packageConfig(Generator21team.getPackageConfig(modelName))
                //自定义生成模板
                .templateConfig(Generator21team.getTemplateConfig())
                //使用Freemarker引擎模板，默认的是Velocity引擎模板
                .templateEngine(new DefaultTemplateEngine())
                .execute();
    }

    /**
     * 通用的配置
     *
     */
    public static Consumer<GlobalConfig.Builder> getGlobalConfig(String author) {
        //默认的生成代码路径
        File file = new File(GENERATOR_LOCAL_PATH);
        if (!file.exists()) {
            boolean mkdirs = file.mkdirs();
            if (!mkdirs) {
                throw new RuntimeException("创建文件夹失败");
            }
        }
        return builder -> {
            builder.author(GENERATOR_AUTHOR)
                    .commentDate(DatePattern.NORM_DATE_PATTERN)
                    //默认不自动打开目录
                    .disableOpenDir()
                    .dateType(DateType.TIME_PACK)
                    .fileOverride()
                    .outputDir(GENERATOR_LOCAL_PATH)
                    .enableSwagger();
        };
    }

    /**
     * 数据源配置
     *
     * @return DataSourceConfig.Build
     */
    public static DataSourceConfig.Builder getDataSourceConfig() {
        DataSourceConfig.Builder builder = new DataSourceConfig.Builder(GENERATOR_URL+"?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true", GENERATOR_USERNAME, GENERATOR_PASSWORD);
        builder.dbQuery(new MySqlQuery());
        return builder;
    }

    /**
     * 策略配置
     *
     * @param modelName 策略配置,传值为2种，如传值为模块名，如admin,则生成 wk_admin_* 所有表，如果传入的是一个具体表名，如wk_admin_user，则生成规则是生成单个表，模块名称取wk_下第二个单词，如wk_admin_user 取 admin
     * @return
     */
    public static Consumer<StrategyConfig.Builder> getStrategyConfig(String modelName) {
        List<IFill> fillList = new ArrayList<>(4);
        fillList.add(new Column("create_time", FieldFill.INSERT));
        fillList.add(new Column("create_user_id", FieldFill.INSERT));
        fillList.add(new Column("update_time", FieldFill.UPDATE));
        fillList.add(new Column("update_user_id", FieldFill.UPDATE));
        return builder -> {
            builder
                    .addTablePrefix("wk_")
                    .likeTable(new LikeTable(modelName.startsWith("wk_") ? modelName : ("wk_" + modelName), SqlLike.RIGHT))
                    .enableSkipView()
                    //idType默认为雪花算法ID
                    .entityBuilder().idType(IdType.ASSIGN_ID).enableChainModel().enableLombok().addTableFills(fillList).build()
                    .controllerBuilder().enableRestStyle().build()
                    .serviceBuilder().superServiceClass("com.kakarote.core.servlet.BaseService").superServiceImplClass("com.kakarote.core.servlet.BaseServiceImpl").build()
                    .mapperBuilder().superClass("com.kakarote.core.servlet.BaseMapper").build();
        };
    }

    /**
     * 包路径相关的配置
     *
     * @param modelName 模块名称
     * @return PackageConfig.Builder
     */
    public static Consumer<PackageConfig.Builder> getPackageConfig(String modelName) {
        if (modelName.startsWith("wk_")) {
            //表名规则应该是 wk_模块名称_业务表名
            modelName = modelName.split("_")[1];
        }
        final String packageName = "com.kakarote." + modelName;
        return builder -> {
            builder.parent(GENERATOR_PACKAGE);
            builder.entity("entity.PO");
        };
    }

    public static Consumer<TemplateConfig.Builder> getTemplateConfig() {
        return builder -> {
            builder.controller("/template21/controller.java");
            builder.service("/template21/service.java");
            builder.serviceImpl("/template21/serviceImpl.java");
            builder.mapper("/template21/mapper.java");
            builder.mapperXml("/template21/mapper.xml");
            builder.entity("/template21/entity.java");
        };
    }
}
