package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record OpenApiMetadata(Optional<SecurityScheme> security, List<ServiceDetail> serviceDetails, String basePath, Duration cacheDuration, OpenApiVersion openApiVersion)
{
    public OpenApiMetadata
    {
        requireNonNull(security, "security is null");
        requireNonNull(basePath, "basePath is null");
        requireNonNull(cacheDuration, "cacheDuration is null");
        requireNonNull(openApiVersion, "openApiVersion is null");
        serviceDetails = ImmutableList.copyOf(serviceDetails);
    }

    public enum SecurityScheme
    {
        BEARER_ACCESS_TOKEN,
        BEARER_JWT,
        BASIC,
    }

    public enum OpenApiVersion
    {
        OPENAPI_3_0_1("3.0.1"),
        OPENAPI_3_2_0("3.2.0");

        private final String value;

        public String value()
        {
            return value;
        }

        OpenApiVersion(String value)
        {
            this.value = value;
        }
    }

    public static final String SECTION_HELP = "Help";
    public static final String SECTION_SERVICES = "Services";
    public static final String SECTION_MODELS = "Models";

    public static final String TAG_MODEL_DEFINITIONS = "Model Definitions";
    public static final String TAG_RESPONSE_DEFINITIONS = "Responses";

    public record ServiceDetail(String section, String label, String description)
    {
        public ServiceDetail
        {
            requireNonNull(section, "section is null");
            requireNonNull(label, "label is null");
            requireNonNull(description, "description is null");
        }
    }
}
