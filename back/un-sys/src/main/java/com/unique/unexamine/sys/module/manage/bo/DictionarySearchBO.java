package com.unique.unexamine.sys.module.manage.bo;

import com.unique.unexamine.core.bo.SearchBO;

/** 字典基础项的业务筛选条件，分页规则复用公共查询对象。 */
public class DictionarySearchBO extends SearchBO {
    private String dictCode;
    private String name;
    private String value;
    private Long parentId;

    public String getDictCode() { return dictCode; }
    public void setDictCode(String dictCode) { this.dictCode = dictCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
}
