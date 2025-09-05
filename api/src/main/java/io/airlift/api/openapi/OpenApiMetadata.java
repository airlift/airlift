package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record OpenApiMetadata(Optional<SecurityScheme> security, List<ServiceDetail> serviceDetails, String baseBath, Duration cacheDuration)
{
    public OpenApiMetadata
    {
        requireNonNull(security, "security is null");
        requireNonNull(baseBath, "baseBath is null");
        serviceDetails = ImmutableList.copyOf(serviceDetails);
    }

    public OpenApiMetadata(Optional<SecurityScheme> security, List<ServiceDetail> serviceDetails)
    {
        this(security, serviceDetails, "/", Duration.ofMinutes(5));
    }

    public enum SecurityScheme
    {
        BEARER_ACCESS_TOKEN,
        BEARER_JWT,
        BASIC,
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
