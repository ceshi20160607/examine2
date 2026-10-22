package com.unique.unexamine.sys.module.manage.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.*;
import com.unique.unexamine.sys.module.base.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 按用户给出的“1 是、0 否”例子验证基础数据、字段配置和业务值的区别。 */
class ModuleFieldServiceTest {
    private final DictionaryService dictionaries=mock(DictionaryService.class);
    private final ModuleFieldService service=new ModuleFieldService(new FieldValidationService(),dictionaries,
            new ObjectMapper(),mock(IUnModuleRecordService.class),mock(IUnModuleRecordDataService.class));
    private final Map<Long,DictionaryService.Item> items=new HashMap<>(Map.of(
            101L,item(101L,"1","是",null,1),
            102L,item(102L,"0","否",null,1),
            103L,item(103L,"other","其他",null,1)));

    private static DictionaryService.Item item(Long id,String value,String name,Long parentId,long revision) {
        return new DictionaryService.Item(id,"BOOLEAN",value,name,parentId,0,id.toString(),0,null,null,false,revision);
    }

    private void resolve() {
        when(dictionaries.items(eq(1L),eq(1L),anyCollection())).thenAnswer(invocation->{
            Map<Long,DictionaryService.Item> result=new LinkedHashMap<>();
            for (Long id:invocation.<Collection<Long>>getArgument(2)) result.put(id,items.get(id));
            return result;
        });
    }
    private FieldDefinition definition(FieldType type,List<Map<String,Object>> options) {
        return service.definition(1L,1L,"choice","是否有效",type.name(),false,Map.of("options",options));
    }

    @Test void referencesAreSeparateFromBasicValueAndCurrentName() {
        resolve();
        var field=definition(FieldType.SINGLE_SELECT,List.of(Map.of("id",102L),Map.of("id",101L)));
        assertEquals(Map.of("options",List.of(Map.of("id",102L),Map.of("id",101L))),field.rules());
        assertDoesNotThrow(()->service.validate(1L,1L,field,102L,null));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,0,null));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,"102",null));
        assertEquals(Map.of("102","否"),service.display(1L,1L,field,102L));
        items.put(102L,item(102L,"0","不是",null,2));
        assertEquals(Map.of("102","不是"),service.display(1L,1L,field,102L));
    }

    @Test void fieldSelectionDoesNotGrowWhenBasicItemsAreAdded() {
        resolve();
        var field=definition(FieldType.SINGLE_SELECT,List.of(Map.of("id",101L)));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,103L,null));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.SINGLE_SELECT,List.of(Map.of("id",101L,"label","复制名称"))));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.SINGLE_SELECT,List.of(Map.of("id",101L),Map.of("id",101L))));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.SINGLE_SELECT,List.of(Map.of("id",1.5))));
    }

    @Test void fieldDisableOnlyAllowsRetainingExistingSelection() {
        resolve();
        var field=definition(FieldType.SINGLE_SELECT,List.of(Map.of("id",101L,"enabled",false),Map.of("id",102L)));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,101L,null));
        assertDoesNotThrow(()->service.validate(1L,1L,field,101L,101L));
        assertDoesNotThrow(()->service.validate(1L,1L,field,102L,101L));
    }

    @Test void cascadeUsesBasicParentIdsAndRejectsBrokenPaths() {
        resolve(); items.put(101L,new DictionaryService.Item(101L,"AREA","country","国家",null,0,"101",0,null,null,false,1));
        items.put(102L,new DictionaryService.Item(102L,"AREA","city","城市",101L,1,"101,102",0,null,null,false,1));
        var field=definition(FieldType.CASCADE,List.of(Map.of("id",101L),Map.of("id",102L)));
        assertDoesNotThrow(()->service.validate(1L,1L,field,List.of(101L,102L),null));
        assertThrows(IllegalArgumentException.class,()->service.validate(1L,1L,field,List.of(102L),null));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.CASCADE,List.of(Map.of("id",102L))));
    }

    @Test void oneFieldCannotMixUnrelatedDictionaryCategories() {
        resolve(); items.put(103L,new DictionaryService.Item(103L,"AREA","other","其他",null,0,"103",0,null,null,false,1));
        assertThrows(IllegalArgumentException.class,()->definition(FieldType.MULTI_SELECT,
                List.of(Map.of("id",101L),Map.of("id",103L))));
    }
}
