package com.unique.examine.ai.plan;

import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiFillResultParserTest {
    private final AiFillResultParser parser = new AiFillResultParser();

    @Test
    void acceptsAndCanonicalizesEverySupportedTypedScalar() {
        assertValue(AiFieldFillFacade.ResultSchema.STRING,
                "\"hello\"", "\"hello\"");
        assertValue(AiFieldFillFacade.ResultSchema.DECIMAL,
                "12.500", "12.5");
        assertValue(AiFieldFillFacade.ResultSchema.INTEGER,
                "12", "12");
        assertValue(AiFieldFillFacade.ResultSchema.BOOLEAN,
                "true", "true");
        assertValue(AiFieldFillFacade.ResultSchema.DATE,
                "\"2026-08-04\"", "\"2026-08-04\"");
        assertValue(AiFieldFillFacade.ResultSchema.DATETIME,
                "\"2026-08-04T08:30:00+08:00\"",
                "\"2026-08-04T00:30:00Z\"");
    }

    @Test
    void lowConfidenceOrClarificationIsStrictButNotConfirmable() {
        var low = parser.parse("""
                {"value":"candidate","confidence":0.40,"clarification":null}
                """, AiFieldFillFacade.ResultSchema.STRING, 0.80);
        var clarification = parser.parse("""
                {"value":null,"confidence":0.70,
                 "clarification":"Which category should be used?"}
                """, AiFieldFillFacade.ResultSchema.STRING, 0.80);

        assertThat(low.confirmable()).isFalse();
        assertThat(low.confidence()).isEqualTo(0.40);
        assertThat(clarification.confirmable()).isFalse();
        assertThat(clarification.clarification()).isNotBlank();
    }

    @Test
    void rejectsUnknownDuplicateAndMismatchedTypedResults() {
        assertInvalid("""
                {"value":"x","confidence":0.9,"clarification":null,"extra":1}
                """, AiFieldFillFacade.ResultSchema.STRING);
        assertInvalid("""
                {"value":"x","value":"y","confidence":0.9,"clarification":null}
                """, AiFieldFillFacade.ResultSchema.STRING);
        assertInvalid("""
                {"value":"not-a-number","confidence":0.9,"clarification":null}
                """, AiFieldFillFacade.ResultSchema.DECIMAL);
        assertInvalid("""
                {"value":"2026-99-99","confidence":0.9,"clarification":null}
                """, AiFieldFillFacade.ResultSchema.DATE);
    }

    private void assertValue(
            AiFieldFillFacade.ResultSchema schema,
            String providerValue,
            String canonicalValue) {
        var result = parser.parse(
                "{\"value\":" + providerValue
                        + ",\"confidence\":0.90,\"clarification\":null}",
                schema, 0.80);
        assertThat(result.confirmable()).isTrue();
        assertThat(result.canonicalOwnerResultJson())
                .isEqualTo("{\"value\":" + canonicalValue
                        + ",\"confidence\":0.9}");
    }

    private void assertInvalid(
            String value, AiFieldFillFacade.ResultSchema schema) {
        assertThatThrownBy(() -> parser.parse(value, schema, 0.80))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_FILL_RESULT_INVALID");
    }
}
