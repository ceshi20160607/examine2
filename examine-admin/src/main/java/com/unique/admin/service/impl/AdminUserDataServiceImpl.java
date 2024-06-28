package com.unique.admin.service.impl;

import com.unique.admin.entity.PO.AdminUserData;
import com.unique.admin.mapper.AdminUserDataMapper;
import com.unique.admin.service.IAdminUserDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户部门数据权限表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-06-28
 */
@Service
public class AdminUserDataServiceImpl extends ServiceImpl<AdminUserDataMapper, AdminUserData> implements IAdminUserDataService {

}
