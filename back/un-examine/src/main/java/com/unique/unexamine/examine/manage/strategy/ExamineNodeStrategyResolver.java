package com.unique.unexamine.examine.manage.strategy;

import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.util.List;
import org.springframework.stereotype.Component;

/** 根据发布快照节点类型选择审批节点策略。 */
@Component
public class ExamineNodeStrategyResolver {
    private final List<ExamineNodeStrategy> strategies;

    public ExamineNodeStrategyResolver(List<ExamineNodeStrategy> strategies) {
        this.strategies = strategies == null ? List.of() : List.copyOf(strategies);
    }

    /** 未注册类型直接失败，避免配置拼写错误静默变成普通审批。 */
    public ExamineNodeStrategy resolve(String nodeType) {
        return strategies.stream().filter(strategy -> strategy.supports(nodeType)).findFirst()
                .orElseThrow(() -> new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                        "未注册的审批节点类型: " + nodeType));
    }
}
