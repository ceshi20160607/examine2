package com.unique.unexamine.core.field;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证自动编号和公式只执行受控规则，避免配置被当作脚本运行。 */
class ComputedFieldServiceTest {
    @Test
    void autoNumberRendersDateAndPaddedSequence() {
        var field=new FieldDefinition("serial_no","编号",FieldType.AUTO_NUMBER,false,
                Map.of("autoNumberConfig",Map.of("prefix","NO-{YYYY}{MM}-","padding",4,"period","MONTH")));
        var rule=ComputedFieldService.autoNumber(field);
        LocalDateTime time=LocalDateTime.of(2026,10,24,12,0);
        assertEquals("NO-202610-0012",rule.render(time,12));
        assertEquals("202610",rule.periodKey(time));
    }

    @Test
    void evaluatesNumericFormulaAndTextTemplate() {
        var numberField=new FieldDefinition("total","合计",FieldType.FORMULA,false,
                Map.of("formulaConfig",Map.of("resultType","NUMBER","expression","price * quantity","scale",2,"round",true)));
        var numberRule=ComputedFieldService.formula(numberField);
        assertEquals(new BigDecimal("37.50"),ComputedFieldService.evaluate(numberRule,Map.of("price",12.5,"quantity",3)));

        var textField=new FieldDefinition("summary","摘要",FieldType.FORMULA,false,
                Map.of("formulaConfig",Map.of("resultType","TEXT","template","${code}-${name}")));
        var textRule=ComputedFieldService.formula(textField);
        assertEquals("A-客户",ComputedFieldService.evaluate(textRule,Map.of("code","A","name","客户")));
    }

    @Test
    void rejectsUnsafeOrCyclicFormulaSyntax() {
        var field=new FieldDefinition("total","合计",FieldType.FORMULA,false,
                Map.of("formulaConfig",Map.of("resultType","NUMBER","expression","price;delete")));
        assertThrows(IllegalArgumentException.class,()->ComputedFieldService.validateDefinition(field));
        var loop=new FieldDefinition("serial_no","编号",FieldType.AUTO_NUMBER,false,Map.of());
        assertThrows(IllegalArgumentException.class,()->ComputedFieldService.validateDefinition(loop));
    }
}
