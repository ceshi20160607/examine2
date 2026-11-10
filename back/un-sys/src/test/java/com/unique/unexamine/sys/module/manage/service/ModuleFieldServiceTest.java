package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.sys.module.manage.dto.DictionaryDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.*;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.module.base.service.*;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 按用户给出的“1 是、0 否”例子验证基础数据、字段配置和业务值的区别。 */
class ModuleFieldServiceTest {
    private final DictionaryService dictionaries=mock(DictionaryService.class);
    private final IUnSysMemberService members=mock(IUnSysMemberService.class);
    private final IUnSysTenantMemberService tenantMembers=mock(IUnSysTenantMemberService.class);
    private final IUnSysDepartmentService departments=mock(IUnSysDepartmentService.class);
    private final ModuleFieldService service=new ModuleFieldService(new FieldValidationService(),dictionaries,
            new ObjectMapper(),mock(IUnModuleRecordService.class),mock(IUnModuleRecordDataService.class),
            members,tenantMembers,departments);
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
        assertThrows(BaseException.class,()->service.validate(1L,1L,field,"",null));
        assertThrows(BaseException.class,()->service.validate(1L,1L,field,"102",null));
        assertEquals(Map.of("0","否"),service.display(1L,1L,field,"0"));
        items.put(102L,item(102L,"0","不是",null));
        assertEquals(Map.of("0","不是"),service.display(1L,1L,field,"0"));
    }

    @Test void fieldSelectionDoesNotGrowWhenBasicItemsAreAdded() {
        resolve();
        var field=definition(FieldType.SELECT,List.of(Map.of("id",101L)));
        assertThrows(BaseException.class,()->service.validate(1L,1L,field,"other",null));
        assertThrows(BaseException.class,()->definition(FieldType.SELECT,List.of(Map.of("id",101L,"label","复制名称"))));
        assertThrows(BaseException.class,()->definition(FieldType.SELECT,List.of(Map.of("id",101L),Map.of("id",101L))));
        assertThrows(BaseException.class,()->definition(FieldType.SELECT,List.of(Map.of("id",1.5))));
    }

    @Test void fieldDisableOnlyAllowsRetainingExistingSelection() {
        resolve();
        var field=definition(FieldType.SELECT,List.of(Map.of("id",101L,"enabled",false),Map.of("id",102L)));
        assertThrows(BaseException.class,()->service.validate(1L,1L,field,"1",null));
        assertDoesNotThrow(()->service.validate(1L,1L,field,"1","1"));
        assertDoesNotThrow(()->service.validate(1L,1L,field,"0","1"));
    }

    @Test void cascadeUsesBasicParentIdsAndRejectsBrokenPaths() {
        resolve(); items.put(101L,new DictionaryDto.Item(101L,"AREA","country","国家",null,0,0,null,null,false));
        items.put(102L,new DictionaryDto.Item(102L,"AREA","city","城市",101L,1,0,null,null,false));
        var field=definition(FieldType.CASCADE,List.of(Map.of("id",101L),Map.of("id",102L)));
        assertDoesNotThrow(()->service.validate(1L,1L,field,Map.of("path",List.of(101L,102L)),null));
        assertThrows(BaseException.class,()->service.validate(1L,1L,field,List.of(101L,102L),null));
        assertThrows(BaseException.class,()->service.validate(1L,1L,field,Map.of("path",List.of(102L)),null));
        assertThrows(BaseException.class,()->definition(FieldType.CASCADE,List.of(Map.of("id",102L))));
    }

    @Test void oneFieldCannotMixUnrelatedDictionaryCategories() {
        resolve(); items.put(103L,new DictionaryDto.Item(103L,"AREA","other","其他",null,0,0,null,null,false));
        assertThrows(BaseException.class,()->definition(FieldType.SELECT,
                List.of(Map.of("id",101L),Map.of("id",103L))));
    }

    @Test void readonlyFieldIsRejectedByTheSharedWriteGuard() {
        var field = new com.unique.unexamine.sys.module.base.entity.UnModuleField();
        field.setFieldName("审批结果");
        field.setReadonlyFlag(true);
        assertThrows(BaseException.class, () -> service.requireEditable(field));
        field.setReadonlyFlag(false);
        assertDoesNotThrow(() -> service.requireEditable(field));
    }

    @Test void departmentValuesUseCurrentTenantDirectoryDisplay() {
        var department = new UnSysDepartment();
        department.setId(7L); department.setSystemId(1L); department.setTenantId(1L);
        department.setParentId(3L); department.setCode("SALES"); department.setName("销售部");
        department.setStatus("ACTIVE");
        when(departments.queryById(7L)).thenReturn(department);
        var field = new FieldDefinition("department", "部门", FieldType.DEPARTMENT, false, Map.of());
        Object present = service.present(1L, 1L, field, 7L);
        assertEquals(new ModuleFieldDto.DepartmentView(7L, 3L, "SALES", "销售部"), present);
    }

    @Test void inactiveMembersAreNotPresentedAsCurrentTenantUsers() {
        var member = new UnSysMember();
        member.setId(9L); member.setSystemId(1L); member.setStatus("DISABLED");
        when(members.getById(9L)).thenReturn(member);
        var field = new FieldDefinition("owner", "负责人", FieldType.USER, false, Map.of());
        assertNull(service.present(1L, 1L, field, 9L));

        member.setStatus("ACTIVE");
        // 查询条件包含 ACTIVE 后，停用的租户关系不会被基础 Service 返回。
        when(tenantMembers.getOne(any(), eq(false))).thenReturn(null);
        assertNull(service.present(1L, 1L, field, 9L));
    }

    @Test void corruptedStructuredValueUsesUnifiedBusinessException() {
        var row = new UnModuleRecordData();
        row.setValueJson("{broken");
        var field = new FieldDefinition("areas", "区域", FieldType.CASCADE, false, Map.of());
        BaseException error = assertThrows(BaseException.class, () -> service.stored(row, field));
        assertEquals(com.unique.unexamine.core.enums.SystemCodeEnum.SYSTEM_CONFLICT.getCode(), error.getCode());
    }
}
