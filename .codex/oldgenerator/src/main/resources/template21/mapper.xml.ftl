<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${package.Mapper}.${table.mapperName}">

<#if enableCache>
    <!-- 开启二级缓存 -->
    <cache type="${cacheClassName}"/>

</#if>
<#if baseResultMap>
    <!-- 通用查询映射结果 -->
    <resultMap id="BaseResultMap" type="${package.Entity}.${entity}">
<#list table.fields as field>
<#if field.keyFlag><#--生成主键排在第一位-->
        <id column="${field.name}" property="${field.propertyName}" />
</#if>
</#list>
<#list table.commonFields as field><#--生成公共字段 -->
        <result column="${field.name}" property="${field.propertyName}" />
</#list>
<#list table.fields as field>
<#if !field.keyFlag><#--生成普通字段 -->
        <result column="${field.name}" property="${field.propertyName}" />
</#if>
</#list>
    </resultMap>

</#if>
<#if baseColumnList>
    <!-- 通用查询结果列 -->
    <sql id="Base_Column_List">
<#list table.commonFields as field>
        ${field.columnName},
</#list>
        ${table.fieldNames}
    </sql>

</#if>

    <select id="queryById" resultType="com.unique.crm.common.CrmModel">
        select a.* from ${table.name} as a
    </select>
    <select id="queryProductList" resultType="com.alibaba.fastjson.JSONObject">
        select sccp.r_id,sccp.product_id,scp.name as product_name,sccp.price,sccp.sales_price,sccp.num,
        sccp.discount,sccp.subtotal,sccp.remark,sccp.cost_price,sccp.single_cost_amount,c.name as
        category_name,scp.batch_id,scp.status,sccp.unit,sccp.contract_id,scp.description as description,scp.num as productDataNum
        FROM ${table.name}_product sccp
        LEFT JOIN wk_crm_product as scp on scp.product_id = sccp.product_id
        left join `wk_crm_product_category` as c on scp.category_id = c.category_id
        <when test="ids !=null and ids.size>0">
            where sccp.contract_id in
            <foreach collection="ids" index="index" open="(" close=")" separator="," item="itemid">
                ${"#"}{itemid}
            </foreach>
        </when>
    </select>
</mapper>
