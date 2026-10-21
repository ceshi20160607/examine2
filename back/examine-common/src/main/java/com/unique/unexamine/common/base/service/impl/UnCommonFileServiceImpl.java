package com.unique.unexamine.common.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.common.base.entity.UnCommonFile;
import com.unique.unexamine.common.base.mapper.UnCommonFileMapper;
import com.unique.unexamine.common.base.service.IUnCommonFileService;
import org.springframework.stereotype.Service;

/** UnCommonFile 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnCommonFileServiceImpl extends ServiceImpl<UnCommonFileMapper, UnCommonFile> implements IUnCommonFileService {
}
