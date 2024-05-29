package com.unique.core.utils;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.unique.core.config.ApproveConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 字段相关处理工具类
 * @author ceshi
 * @date 2024/05/28
 */
public class FieldUtil {


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


}
