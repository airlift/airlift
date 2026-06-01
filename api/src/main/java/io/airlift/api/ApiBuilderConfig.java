package io.airlift.api;

import io.airlift.api.internals.JacksonEnumValueResolver;

import static java.util.Objects.requireNonNull;

public final class ApiBuilderConfig
{
    private final ApiEnumValueResolver enumValueResolver;

    private ApiBuilderConfig(ApiEnumValueResolver enumValueResolver)
    {
        this.enumValueResolver = requireNonNull(enumValueResolver, "enumValueResolver is null");
    }

    public static ApiBuilderConfig jackson()
    {
        return new ApiBuilderConfig(new JacksonEnumValueResolver());
    }

    public static ApiBuilderConfig of(ApiEnumValueResolver enumValueResolver)
    {
        return new ApiBuilderConfig(enumValueResolver);
    }

    public ApiEnumValueResolver enumValueResolver()
    {
        return enumValueResolver;
    }
}
