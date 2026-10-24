package com.unique.unexamine.core.bo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;

/** 基础分页查询对象；业务查询可继承并增加有明确含义的条件。 */
public class SearchBO {
    /** 当前页，从一开始。 */
    private Integer page = 1;
    /** 单页记录数。 */
    private Integer limit = 15;
    /** 业务自行决定参与匹配的字段，基础分页不猜测列名。 */
    private String keyword;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    /** 将公共分页参数交给 MyBatis-Plus，筛选条件仍由所属业务处理。 */
    public <T> Page<T> parse() {
        return parse(100);
    }

    /** 导出等明确受控的业务可指定更大的单次分页上限。 */
    public <T> Page<T> parse(int maxLimit) {
        if (page == null || page < 1 || limit == null || limit < 1 || limit > maxLimit) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "分页范围无效");
        }
        return Page.of(page, limit);
    }
}
