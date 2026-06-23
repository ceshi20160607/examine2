package com.unique.examine.core.flow;

/**
 * 业务记录与流程运行态的解耦接口。
 */
public interface FlowRecordRuntimeService {

    /**
     * 为业务记录创建流程实例和首个待办。
     *
     * @param request 发起流程入参
     * @return 流程启动结果
     */
    FlowRecordStartResult startForRecord(FlowRecordStartRequest request);
}
