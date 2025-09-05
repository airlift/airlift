package io.airlift.api.validation;

import io.airlift.api.ApiDeprecated;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelDeprecation;
import io.airlift.api.model.ModelServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static io.airlift.api.builders.ApiBuilder.apiBuilder;
import static org.assertj.core.api.Assertions.assertThat;

public class TestDeprecation
{
    @Test
    public void testDeprecatedMethod()
            throws NoSuchMethodException
    {
        ValidationContext validationContext = new ValidationContext();
        ModelServices services = validationContext.withContext("", _ -> apiBuilder().add(Service.class).build()).map(ModelApi::modelServices).orElseThrow();

        assertThat(services.deprecations()).withFailMessage(() -> services.errors().toString()).hasSize(1);
        ModelDeprecation modelDeprecation = services.deprecations().iterator().next();
        assertThat(modelDeprecation.deprecationDate()).isNotEmpty();
        assertThat(modelDeprecation.newImplementation()).isNotEmpty();
        assertThat(modelDeprecation.method()).isEqualTo(Service.class.getMethod("oldGetThing"));
    }

    @Test
    public void testDeprecatedDateFormats()
    {
        Instant instant = DeprecationValidator.toDeprecationDate(getApiDeprecated()).orElseThrow(() -> new AssertionError("Date parsing failed"));
        assertThat(instant.toString()).isEqualTo("1994-11-06T23:59:59Z");
    }

    private ApiDeprecated getApiDeprecated()
    {
        try {
            Method declaredMethod = getClass().getDeclaredMethod("deprecated");
            ApiDeprecated annotation = declaredMethod.getAnnotation(ApiDeprecated.class);
            assertThat(annotation).isNotNull();
            return annotation;
        }
        catch (NoSuchMethodException e) {
            throw new AssertionError("Could not find method: " + "deprecated");
        }
    }

    @SuppressWarnings("unused")
    @ApiDeprecated(information = "", deprecationDate = "1994-11-06")
    private void deprecated()
    {
    }
}
