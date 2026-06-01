package io.airlift.api.internals;

import com.fasterxml.jackson.annotation.JsonValue;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.validation.ValidatorException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJacksonEnumValueResolver
{
    @Test
    public void testJsonValueInvocationExceptionPreservesCause()
    {
        assertThatThrownBy(() -> ApiBuilderConfig.jackson().enumValueResolver().value(ThrowingJsonValueEnum.Small))
                .isInstanceOf(ValidatorException.class)
                .hasMessage("[@JsonValue method value threw an exception for enum io.airlift.api.internals.TestJacksonEnumValueResolver$ThrowingJsonValueEnum]")
                .hasCauseInstanceOf(InvocationTargetException.class);
    }

    private enum ThrowingJsonValueEnum
    {
        Small;

        @JsonValue
        public String value()
        {
            throw new IllegalStateException("no value");
        }
    }
}
