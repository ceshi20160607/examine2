package com.unique.admin.mapper;

import com.unique.admin.entity.po.AdminDept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.entity.user.bo.SimpleDept;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 部门表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2023-03-25
 */
public interface AdminDeptMapper extends BaseMapper<AdminDept> {

    BasePage<List<AdminDept>> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

    List<SimpleDept> queryDataDepts(Long userId);

    List<SimpleDept> queryAllDepts();
}
