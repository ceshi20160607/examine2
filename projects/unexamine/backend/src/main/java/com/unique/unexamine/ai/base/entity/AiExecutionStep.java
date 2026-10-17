package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * AI 执行步骤、工具调用与结果摘要
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_execution_step")
public class AiExecutionStep {

    /**
     * 执行步骤主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属执行标识
     */
    @TableField("execution_id")
    private Long executionId;

    /**
     * 执行 Agent 标识，用于限制工具归属
     */
    @TableField("agent_id")
    private Long agentId;

    /**
     * 本次执行内步骤序号
     */
    @TableField("step_number")
    private Integer stepNumber;

    /**
     * 步骤类别
     */
    @TableField("step_type")
    private String stepType;

    /**
     * 使用的 Agent 工具标识
     */
    @TableField("tool_id")
    private Long toolId;

    /**
     * 步骤输入摘要，不含密钥明文
     */
    @TableField("input_json")
    private String inputJson;

    /**
     * 步骤输出及脱敏摘要
     */
    @TableField("output_json")
    private String outputJson;

    /**
     * 步骤状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 步骤失败摘要
     */
    @TableField("error_message")
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Integer getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(Integer stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public Long getToolId() {
        return toolId;
    }

    public void setToolId(Long toolId) {
        this.toolId = toolId;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public void setOutputJson(String outputJson) {
        this.outputJson = outputJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "AiExecutionStep{" +
            "id = " + id +
            ", executionId = " + executionId +
            ", agentId = " + agentId +
            ", stepNumber = " + stepNumber +
            ", stepType = " + stepType +
            ", toolId = " + toolId +
            ", inputJson = " + inputJson +
            ", outputJson = " + outputJson +
            ", status = " + status +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", errorMessage = " + errorMessage +
            "}";
    }
}
