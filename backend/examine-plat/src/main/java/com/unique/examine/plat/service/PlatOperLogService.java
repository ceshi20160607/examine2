package com.unique.examine.plat.service;

import com.unique.examine.plat.entity.PO.PlatOperLog;
import com.unique.examine.plat.mapper.PlatOperLogMapper;
import org.springframework.stereotype.Service;

@Service
public class PlatOperLogService {

    private final PlatOperLogMapper mapper;

    public PlatOperLogService(PlatOperLogMapper mapper) {
        this.mapper = mapper;
    }

    public void save(PlatOperLog log) {
        mapper.insert(log);
    }
}

