package com.unique.unexamine.core.permission;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.unique.unexamine.core.exception.BaseException;
import org.junit.jupiter.api.Test;

/** 服务端过滤不得改变拖拽布局，也不能把字段可读视为可写。 */
class PermissionPolicyTest {
    @Test void filtersLayoutWithoutReflowAndRejectsForbiddenWritesAndOwners() {
        var policy=new PermissionPolicy(true,false,Set.of(9L),false,Set.of("name"),false,Set.of(),false,Set.of());
        var name=Map.<String,Object>of("code","name","layout",Map.of("rowId","single","width","30%"));
        var result=policy.filterReadable(List.of(name,Map.<String,Object>of("code","amount")),row->(String)row.get("code"));
        assertEquals(1,result.size()); assertSame(name,result.getFirst());
        assertThrows(BaseException.class,()->policy.requireWritable(Set.of("name")));
        assertThrows(BaseException.class,()->policy.requireOwner(10L));
        assertDoesNotThrow(()->policy.requireOwner(9L));
    }
    @Test void absentActionDeniesEvenWhenAllFieldsAndDataWerePresent() {
        var policy=new PermissionPolicy(false,true,Set.of(),true,Set.of(),true,Set.of(),true,Set.of());
        assertThrows(BaseException.class,()->policy.requireAction());
        assertThrows(BaseException.class,()->policy.filterReadable(List.of("name"),s->s));
    }
    @Test void exportPermissionNeverExceedsReadableFields() {
        var policy=new PermissionPolicy(true,true,Set.of(),false,Set.of("name"),false,Set.of(),false,Set.of("name","amount"));
        assertTrue(policy.exportable("name"));
        assertFalse(policy.exportable("amount"));
        assertFalse(policy.writable("name"));
    }
}
