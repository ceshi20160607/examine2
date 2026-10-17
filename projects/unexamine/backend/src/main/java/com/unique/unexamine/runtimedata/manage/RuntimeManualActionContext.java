package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 已授权来源动作的实际参数；只返回该动作所需可填写字段，不替代模块或审批权限。 */
public record RuntimeManualActionContext(String actionCode, String name, RuntimeRecordView record,
                                         List<JsonNode> parameters, boolean confirmation, String confirmationText,
                                         String operation, String statusValue) {
}
