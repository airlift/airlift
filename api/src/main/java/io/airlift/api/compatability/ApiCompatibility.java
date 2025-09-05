package io.airlift.api.compatability;

import com.google.inject.Inject;
import io.airlift.api.model.ModelServices;

import java.io.File;

public class ApiCompatibility
{
    @Inject
    public ApiCompatibility(ModelServices modelServices)
    {
        File sourcePath = new File(ApiCompatibilityUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        File testResourcesPath = new File(sourcePath.getParentFile().getParentFile(), "src/test/resources/api/compatibility");

        ApiCompatibilityTester.newDefaultInstance()
                .test(modelServices, testResourcesPath.getAbsolutePath());
    }
}
