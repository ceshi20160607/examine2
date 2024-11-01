package com.unique.core.enums;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 时间单位 枚举
 * @author UNIQUE
 * @date 2024/10/29
 */

public enum DateTypeUnitEnum {

    DAY(1, "天"),
    WEEK(2, "周"),
    MONTH(3, "月"),
    QUARTER(4, "季度"),
    YEAR(5, "年"),

    ;

    DateTypeUnitEnum(Integer type, String remark) {
        this.type = type;
        this.remark = remark;
    }

    private final Integer type;
    private final String remark;

    public String getRemark() {
        return remark;
    }

    public Integer getType() {
        return type;
    }

    public static DateTypeUnitEnum parse(Integer type) {
        for (DateTypeUnitEnum crmEnum : values()) {
            if (crmEnum.getType().equals(type)) {
                return crmEnum;
            }
        }
        return null;
    }

    /**
     * 获取时间范围内的 日期不同类型的 时间集合
     * @param starTime
     * @param endTime
     * @param unitEnum
     * @return {@link Map }<{@link String },{@link String }>
     */
    public static Map<String,String> range(Date starTime, Date endTime, DateTypeUnitEnum unitEnum) {
        Map<String, String> rangeTimeMap = new HashMap<>();
        if (ObjectUtil.isEmpty(starTime) ||ObjectUtil.isEmpty(endTime)||starTime.after(endTime)) {
            return rangeTimeMap;
        }
        if (ObjectUtil.isEmpty(unitEnum)) {
            unitEnum = DateTypeUnitEnum.DAY;
        }
        switch (unitEnum) {
            case DAY:
                rangeTimeMap = DateUtil.rangeToList(starTime, endTime, DateField.DAY_OF_YEAR).stream().collect(Collectors.toMap(r-> DateUtil.formatDate(r), r-> DateUtil.formatDate(r)));
                break;
            case WEEK:
                rangeTimeMap = DateUtil.rangeToList(starTime, endTime, DateField.WEEK_OF_YEAR).stream().collect(Collectors.toMap(r-> String.valueOf(r.year()+r.weekOfYear()), r-> DateUtil.format(DateUtil.beginOfWeek(r), "MM-dd")+"~"+DateUtil.format(DateUtil.endOfWeek(r),"MM-dd")));
                break;
            case MONTH:
                rangeTimeMap = DateUtil.rangeToList(starTime, endTime, DateField.MONTH).stream().collect(Collectors.toMap(r-> String.valueOf(r.year()+r.month()), r-> r.year()+"-"+ (r.month()+1)));
                break;
            case QUARTER:
                Map<String, List<DateTime>> tempGroup = DateUtil.rangeToList(starTime, endTime, DateField.DAY_OF_YEAR).stream().collect(Collectors.groupingBy(r -> r.year() + "-Q" + r.quarter()));
                Map<String, String> finalRet = new HashMap<>();
                tempGroup.forEach((k, v)->{
                    finalRet.put(k, k+" "+DateUtil.format(DateUtil.beginOfQuarter(v.get(0)), "MM-dd")+"~"+DateUtil.format(DateUtil.endOfQuarter(v.get(0)),"MM-dd"));
                });
                rangeTimeMap = finalRet;
                break;
            case YEAR:
                rangeTimeMap = DateUtil.rangeToList(starTime, endTime, DateField.YEAR).stream().collect(Collectors.toMap(r-> String.valueOf(r.year()), r-> String.valueOf(r.year())));
                break;
        }
        return MapUtil.sort(rangeTimeMap);
    }
}
