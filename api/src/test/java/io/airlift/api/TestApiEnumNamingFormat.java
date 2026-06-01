package io.airlift.api;

import org.junit.jupiter.api.Test;

import static io.airlift.api.ApiEnumNamingFormat.PASCAL_CASE;
import static io.airlift.api.ApiEnumNamingFormat.UPPER_SNAKE_CASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestApiEnumNamingFormat
{
    @Test
    public void testStringValue()
    {
        assertThat(PASCAL_CASE).hasToString("PascalCase");
        assertThat(UPPER_SNAKE_CASE).hasToString("UPPER_SNAKE_CASE");
    }

    @Test
    public void testFromString()
    {
        assertThat(ApiEnumNamingFormat.fromString("PascalCase")).isEqualTo(PASCAL_CASE);
        assertThat(ApiEnumNamingFormat.fromString("UPPER_SNAKE_CASE")).isEqualTo(UPPER_SNAKE_CASE);

        assertThatThrownBy(() -> ApiEnumNamingFormat.fromString("PASCAL_CASE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown enum naming format: PASCAL_CASE");
    }

    @Test
    public void testPascalCaseValidation()
    {
        assertThat(PASCAL_CASE.isValid("OrderStatus")).isTrue();
        assertThat(PASCAL_CASE.isValid("A")).isTrue();
        assertThat(PASCAL_CASE.isValid("Status1")).isTrue();

        assertThat(PASCAL_CASE.isValid("orderStatus")).isFalse();
        assertThat(PASCAL_CASE.isValid("_Order")).isFalse();
        assertThat(PASCAL_CASE.isValid("123")).isFalse();
        assertThat(PASCAL_CASE.isValid("")).isFalse();
    }

    @Test
    public void testUpperSnakeCaseValidation()
    {
        assertThat(UPPER_SNAKE_CASE.isValid("ORDER_STATUS")).isTrue();
        assertThat(UPPER_SNAKE_CASE.isValid("A_B")).isTrue();
        assertThat(UPPER_SNAKE_CASE.isValid("STATUS_1")).isTrue();

        assertThat(UPPER_SNAKE_CASE.isValid("Order_Status")).isFalse();
        assertThat(UPPER_SNAKE_CASE.isValid("_ORDER")).isFalse();
        assertThat(UPPER_SNAKE_CASE.isValid("ORDER_")).isFalse();
        assertThat(UPPER_SNAKE_CASE.isValid("ORDER__STATUS")).isFalse();
        assertThat(UPPER_SNAKE_CASE.isValid("")).isFalse();
    }

    @Test
    public void testValidationRejectsNull()
    {
        assertThatThrownBy(() -> PASCAL_CASE.isValid(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value is null");
    }
}
