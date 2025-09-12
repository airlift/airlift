package io.airlift.api.validation;

import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMinimalErrorReporting
{
    @Test
    public void testMinimalErrorReporting()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithLotsOfDuplicateErrors.class).build().modelServices();
        assertThat(services.errors()).hasSize(1);
    }
}
