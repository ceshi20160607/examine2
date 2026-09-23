package com.unique.unexamine.runtimedata.manage;

import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;

/** 已授权编辑记录及其来源发布表单；编辑权限不隐式授予详情权限。 */
public record RuntimeRecordEditContext(RuntimeRecordView record, RuntimeModuleConfiguration configuration) {
}
