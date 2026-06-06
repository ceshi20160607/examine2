package com.unique.examine.manage.service;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.vo.*;

public interface ConfigManageService {
    PageResult<SimpleVO> apps(long pageNo, long pageSize, Long systemId, Long tenantId);
    SimpleVO saveApp(AppSaveBO bo);
    SimpleVO publishApp(Long appId);
    PageResult<SimpleVO> modules(long pageNo, long pageSize, Long appId);
    SimpleVO saveModule(ModuleSaveBO bo);
    PageResult<SimpleVO> fields(long pageNo, long pageSize, Long moduleId);
    SimpleVO saveField(FieldSaveBO bo);
    SimpleVO saveFieldOption(FieldOptionSaveBO bo);
    PageResult<SimpleVO> pages(long pageNo, long pageSize, Long moduleId);
    SimpleVO savePage(PageSaveBO bo);
    SimpleVO publishPage(Long pageId);
    PageResult<SimpleVO> menus(long pageNo, long pageSize, Long systemId, Long tenantId);
    SimpleVO saveMenu(RuntimeMenuSaveBO bo);
    PageResult<SimpleVO> dictionaries(long pageNo, long pageSize, Long systemId, Long tenantId);
    SimpleVO saveDictionary(DictionarySaveBO bo);
    SimpleVO saveDictionaryItem(DictionaryItemSaveBO bo);
}
