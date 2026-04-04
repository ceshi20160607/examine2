package com.unique.examine.core.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.PlatMsg;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.mapper.PlatMsgMapper;
import org.springframework.stereotype.Service;

@Service
public class PlatMsgService extends ServiceImpl<PlatMsgMapper, PlatMsg> {

    public void saveOrUpdateMsg(PlatMsg m) {
        if (m.getMsgType() == null || m.getMsgType().isBlank()) {
            throw new BusinessException("msgType 不能为空");
        }
        if (m.getTitle() == null || m.getTitle().isBlank()) {
            throw new BusinessException("title 不能为空");
        }
        if (m.getId() == null) {
            save(m);
        } else {
            updateById(m);
        }
    }
}
