package io.airlift.api.compatability;

import com.google.inject.Inject;
import io.airlift.api.model.ModelServices;

public class ApiCompatibility
{
    @Inject
    public ApiCompatibility(ApiCompatibilityTester tester, ModelServices modelServices)
    {
        tester.test(modelServices);
    }
}
