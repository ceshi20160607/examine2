package com.kakarote.generator.config;

import cn.hutool.core.date.DatePattern;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.enums.SqlLike;
import com.baomidou.mybatisplus.generator.IFill;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.po.LikeTable;
import com.baomidou.mybatisplus.generator.config.querys.MySqlQuery;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.fill.Column;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


/**
 * 默认的代码生成器配置
 *
 * 
 * @since 2021-11-15
 */
public class GeneratorOwner {

    /**
     * 通用的配置
     *
     * @param author 作者
     * @return GlobalConfig.Builder
     */
    public static Consumer<GlobalConfig.Builder> getGlobalConfig(String author) {
        //默认的生成代码路径
        String path = "D:/generator";
        File file = new File(path);
        if (!file.exists()) {
            boolean mkdirs = file.mkdirs();
            if (!mkdirs) {
                throw new RuntimeException("创建文件夹失败");
            }
        }
        return builder -> {
            builder.author(author)
                    .commentDate(DatePattern.NORM_DATE_PATTERN)
                    //默认不自动打开目录
                    .disableOpenDir()
                    .dateType(DateType.TIME_PACK)
                    .fileOverride()
                    .outputDir(path)
                    .enableSwagger();
        };
    }

    /**
     * 数据源配置
     *
     * @return DataSourceConfig.Build
     */
    public static DataSourceConfig.Builder getDataSourceConfig() {
        DataSourceConfig.Builder builder = new DataSourceConfig.Builder("jdbc:mysql://192.168.0.6:3306/aaaagenger?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true", "root", "password");
        builder.dbQuery(new MySqlQuery());
        return builder;
    }

    /**
     * 策略配置
     *
     * @param modelName 策略配置,传值为2种，如传值为模块名，如admin,则生成 un_admin_* 所有表，如果传入的是一个具体表名，如un_admin_user，则生成规则是生成单个表，模块名称取wk_下第二个单词，如wk_admin_user 取 admin
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
                    .addTablePrefix("un_")
                    .likeTable(new LikeTable(modelName.startsWith("un_") ? modelName : ("un_" + modelName), SqlLike.RIGHT))
                    .enableSkipView()
                    //idType默认为雪花算法ID
                    .entityBuilder().idType(IdType.ASSIGN_ID).enableChainModel().enableLombok().addTableFills(fillList).build()
                    .controllerBuilder().enableRestStyle().build()
                    .serviceBuilder()
                    .mapperBuilder()
                    .build()
            ;
        };
    }

    /**
     * 包路径相关的配置
     *
     * @param modelName 模块名称
     * @return PackageConfig.Builder
     */
    public static Consumer<PackageConfig.Builder> getPackageConfig(String modelName) {
        if (modelName.startsWith("un_")) {
            //表名规则应该是 un_模块名称_业务表名
            modelName = modelName.split("_")[1];
        }
        final String packageName = "com.unique.examine." + modelName;
        return builder -> {
            builder.parent(packageName);
            builder.entity("entity.po");
        };
    }

    public static Consumer<TemplateConfig.Builder> getTemplateConfig() {
        return builder -> {
            builder.controller("/template_owner/controller.java");
            builder.service("/template_owner/service.java");
            builder.serviceImpl("/template_owner/serviceImpl.java");
            builder.mapper("/template_owner/mapper.java");
            builder.mapperXml("/template_owner/mapper.xml");
            builder.entity("/template_owner/entity.java");
        };
    }
}
