package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.sys.module.manage.dto.DictionaryDto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.*;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.module.base.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 按用户给出的“1 是、0 否”例子验证基础数据、字段配置和业务值的区别。 */
class ModuleFieldServiceTest {
    private final DictionaryService dictionaries=mock(DictionaryService.class);
    private final ModuleFieldService service=new ModuleFieldService(new FieldValidationService(),dictionaries,
            new ObjectMapper(),mock(IUnModuleRecordService.class),mock(IUnModuleRecordDataService.class),
            mock(IUnSysMemberService.class),mock(IUnSysTenantMemberService.class),mock(IUnSysDepartmentService.class));
    private final Map<Long,DictionaryDto.Item> items=new HashMap<>(Map.of(
            101L,item(101L,"1","是",null),
            102L,item(102L,"0","否",null),
            103L,item(103L,"other","其他",null)));

    private static DictionaryDto.Item item(Long id,String value,String name,Long parentId) {
        return new DictionaryDto.Item(id,"BOOLEAN",value,name,parentId,0,0,null,null,false);
    }

    private void resolve() {
        when(dictionaries.items(eq(1L),eq(1L),anyCollection())).thenAnswer(invocation->{
            Map<Long,DictionaryDto.Item> result=new LinkedHashMap<>();
            for (Long id:invocation.<Collection<Long>>getArgument(2)) result.put(id,items.get(id));
            return result;
        });
    }
    private FieldDefinition definition(FieldType type,List<Map<String,Object>> options) {
        return service.definition(1L,1L,"choice","是否有效",type.code(),false,Map.of("options",options));
    }

    @Test void referencesAreSeparateFromBasicValueAndCurrentName() {
        resolve();
        var field=definition(FieldType.SELECT,List.of(Map.of("id",102L),Map.of("id",101L)));
        assertEquals(Map.of("options",List.of(Map.of("id",102L),Map.of("id",101L))),field.rules());
        assertDoesNotThrow(()->service.validate(1L,1L,field,"0",null));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,"",null));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,"102",null));
        assertEquals(Map.of("0","否"),service.display(1L,1L,field,"0"));
        items.put(102L,item(102L,"0","不是",null));
        assertEquals(Map.of("0","不是"),service.display(1L,1L,field,"0"));
    }

    @Test void fieldSelectionDoesNotGrowWhenBasicItemsAreAdded() {
        resolve();
        var field=definition(FieldType.SELECT,List.of(Map.of("id",101L)));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,"other",null));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.SELECT,List.of(Map.of("id",101L,"label","复制名称"))));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.SELECT,List.of(Map.of("id",101L),Map.of("id",101L))));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.SELECT,List.of(Map.of("id",1.5))));
    }

    @Test void fieldDisableOnlyAllowsRetainingExistingSelection() {
        resolve();
        var field=definition(FieldType.SELECT,List.of(Map.of("id",101L,"enabled",false),Map.of("id",102L)));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,"1",null));
        assertDoesNotThrow(()->service.validate(1L,1L,field,"1","1"));
        assertDoesNotThrow(()->service.validate(1L,1L,field,"0","1"));
    }

    @Test void cascadeUsesBasicParentIdsAndRejectsBrokenPaths() {
        resolve(); items.put(101L,new DictionaryDto.Item(101L,"AREA","country","国家",null,0,0,null,null,false));
        items.put(102L,new DictionaryDto.Item(102L,"AREA","city","城市",101L,1,0,null,null,false));
        var field=definition(FieldType.CASCADE,List.of(Map.of("id",101L),Map.of("id",102L)));
        assertDoesNotThrow(()->service.validate(1L,1L,field,Map.of("path",List.of(101L,102L)),null));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,List.of(101L,102L),null));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,Map.of("path",List.of(102L)),null));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.CASCADE,List.of(Map.of("id",102L))));
    }

    @Test void oneFieldCannotMixUnrelatedDictionaryCategories() {
        resolve(); items.put(103L,new DictionaryDto.Item(103L,"AREA","other","其他",null,0,0,null,null,false));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.SELECT,
                List.of(Map.of("id",101L),Map.of("id",103L))));
    }
}
