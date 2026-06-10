package io.airlift.api.compatability;

import com.google.inject.Inject;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.model.ModelServices;

public class ApiCompatibility
{
    @Inject
    public ApiCompatibility(ApiCompatibilityTester tester, ModelServices modelServices)
    {
        this(tester, modelServices, ApiBuilderConfig.jackson().enumValueResolver());
    }

    public ApiCompatibility(ApiCompatibilityTester tester, ModelServices modelServices, ApiEnumValueResolver enumValueResolver)
    {
        tester.test(modelServices, enumValueResolver);
    }
}
