package com.unique.core.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.unique.core.config.ApproveConfig;
import com.unique.core.enums.FieldEnum;
import com.unique.core.enums.FieldTypeEnum;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 字段相关处理工具类
 * @author ceshi
 * @date 2024/05/28
 */
public class FieldUtil {


    /**
     * 将field进行行分组
     * @param fieldList
     * @param groupFun
     * @param groupSort
     * @return {@link List }<{@link List }<{@link T }>>
     */
    public static <T> List<List<T>> getFieldFormList(List<T> fieldList, Function<T,Integer> groupFun,Function<T,Integer> groupSort){
        List<List<T>> list = new ArrayList<>();
        Map<Integer, List<T>> fildMap = fieldList.stream().collect(Collectors.groupingBy(groupFun));

        if (ObjectUtil.isNotEmpty(fildMap)) {
            fildMap.forEach((k,v)->{
                list.add(v.stream().sorted(Comparator.comparing(groupSort)).collect(Collectors.toList()));
            });
        }
        return list;
    }


    /**
     * 自定义字段时候用于构建自定义字段的名称
     * @param hadUserdFieldNames
     * @param type
     * @param canUseMainFieldNameListMap
     * @return {@link String }
     */
    public static final String getCanUseFieldName(List<String> hadUserdFieldNames,Integer type,Map<Integer, List<String>> canUseMainFieldNameListMap){
        String retFieldName = "field"+ RandomUtil.randomString(6);
        //处理新建的字段
        Integer mainType = 1;
        switch (FieldEnum.parse(type)) {
            case NUMBER:
                mainType = FieldEnum.NUMBER.getType();
                break;
            case NUMBER_FLOAT:
            case PERCENT:
                mainType = FieldEnum.NUMBER_FLOAT.getType();
                break;
            case RELATION:
                mainType = FieldEnum.RELATION.getType();
                break;
            case DATE:
            case DATETIME:
                mainType = FieldEnum.DATETIME.getType();
                break;
        }

        List<String> strings = canUseMainFieldNameListMap.get(mainType);
        //过滤不能使用的
        List<String> useFields = strings.stream().filter(s -> !hadUserdFieldNames.contains(s)).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(useFields)) {
            retFieldName = strings.get(0);
        }
        return retFieldName;
    }
}
