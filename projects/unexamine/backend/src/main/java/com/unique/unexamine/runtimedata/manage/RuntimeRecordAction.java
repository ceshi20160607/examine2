package com.unique.unexamine.runtimedata.manage;

/** 记录当前允许的真实来源发布动作；展示配置只保留确认要求，执行参数另走受限上下文。 */
public record RuntimeRecordAction(Long id, String code, String name, String actionType, String location,
                                  String status, String configJson, Integer version) {
}
