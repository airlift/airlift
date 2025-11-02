package io.airlift.api.servertests.integration.testingserver;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.api.binding.ApiModule;
import io.airlift.api.compatability.ApiCompatibilityUtil;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiMetadata.ServiceDetail;
import io.airlift.api.servertests.integration.testingserver.external.PublicWidgetApi;

import java.util.Optional;

import static io.airlift.api.compatability.ApiCompatibilityTester.basePathFromClass;
import static io.airlift.api.compatability.ApiCompatibilityTester.newDefaultInstance;
import static io.airlift.api.openapi.OpenApiMetadata.SECTION_HELP;
import static io.airlift.api.openapi.OpenApiMetadata.SecurityScheme.BEARER_ACCESS_TOKEN;

public class TestingApiModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        ServiceDetail serviceDetail = new ServiceDetail(SECTION_HELP, "API Builder", """
                Markdown is supported for service details:

                ```
                Even code blocks
                ```
                """);

        OpenApiMetadata openApiMetadata = new OpenApiMetadata(Optional.of(BEARER_ACCESS_TOKEN), ImmutableList.of(serviceDetail));

        Module apiModule = ApiModule.builder()
                .addApi(apiBuilder -> apiBuilder.add(PublicWidgetApi.class))
                .withApiLogging()
                .withOpenApiMetadata(openApiMetadata)
                .withOpenApiFilterBinding(binding -> binding.to(TestingOpenApiFilter.class))
                .withCompatibilityTester(newDefaultInstance(basePathFromClass(ApiCompatibilityUtil.class)))
                .build();
        binder.install(apiModule);
    }
}
