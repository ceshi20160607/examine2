package com.unique.examine.handler;

import cn.hutool.core.collection.CollectionUtil;
import com.unique.examine.entity.dto.ExamineContext;
import com.unique.examine.entity.po.ExamineRecordNode;
import com.unique.examine.enums.ExamineNodeTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * @author UNIQUE
 * @date 2024/03/05
 */
public abstract class AbstractHandler {
    @Autowired
    public HandlerService handlerService;

    public abstract ExamineNodeTypeEnum examineNodeTypeEnum();
    public abstract void build(ExamineContext context);


    public abstract void handle(ExamineContext context);
}
