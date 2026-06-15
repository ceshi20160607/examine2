package com.kakarote.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.kakarote.generator.config.GeneratorConfig;
import com.kakarote.generator.config.GeneratorOwner;
import com.kakarote.generator.engine.DefaultTemplateEngine;

import java.util.Scanner;

public class Generator {
//    public static void main(String[] args) {
//        System.out.println("请输入模块名称：");
//        Scanner input = new Scanner(System.in);
//        String modelName = input.next();
//        System.out.println("请输入作者名称：");
//        String author = input.next();
//        FastAutoGenerator
//                //数据源配置
//                .create(GeneratorConfig.getDataSourceConfig())
//                //全局配置
//                .globalConfig(GeneratorConfig.getGlobalConfig(author))
//                //策略配置,传值为2种，如传值为模块名，如admin,则生成 wk_admin_* 所有表，如果传入的是一个具体表名，如wk_admin_user，则生成规则是生成单个表，模块名称取wk_下第二个单词，如wk_admin_user 取 admin
//                .strategyConfig(GeneratorConfig.getStrategyConfig(modelName))
//                //包相关信息
//                .packageConfig(GeneratorConfig.getPackageConfig(modelName))
//                //自定义生成模板
//                .templateConfig(GeneratorConfig.getTemplateConfig())
//                //使用Freemarker引擎模板，默认的是Velocity引擎模板
//                .templateEngine(new DefaultTemplateEngine())
//                .execute();
//    }


    public static void main(String[] args) {
        System.out.println("请输入模块名称：");
        Scanner input = new Scanner(System.in);
        String modelName = input.next();
        String author = "UNIQUE";
        FastAutoGenerator
                //数据源配置
                .create(GeneratorOwner.getDataSourceConfig())
                //全局配置
                .globalConfig(GeneratorOwner.getGlobalConfig(author))
                //策略配置,传值为2种，如传值为模块名，如admin,则生成 wk_admin_* 所有表，如果传入的是一个具体表名，如wk_admin_user，则生成规则是生成单个表，模块名称取wk_下第二个单词，如wk_admin_user 取 admin
                .strategyConfig(GeneratorOwner.getStrategyConfig(modelName))
                //包相关信息
                .packageConfig(GeneratorOwner.getPackageConfig(modelName))
                //自定义生成模板
                .templateConfig(GeneratorOwner.getTemplateConfig())
                //使用Freemarker引擎模板，默认的是Velocity引擎模板
                .templateEngine(new DefaultTemplateEngine())
                .execute();
    }
}
