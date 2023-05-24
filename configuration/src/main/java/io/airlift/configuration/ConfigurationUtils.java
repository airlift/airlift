package io.airlift.configuration;

import com.google.common.annotations.VisibleForTesting;

import java.util.Map;
import java.util.function.BiConsumer;

public class ConfigurationUtils
{
    private ConfigurationUtils() {}

    public static Map<String, String> replaceEnvironmentVariables(Map<String, String> properties)
    {
        return replaceEnvironmentVariables(properties, System.getenv(), (k, v) -> {});
    }

    @VisibleForTesting
    public static Map<String, String> replaceEnvironmentVariables(
            Map<String, String> properties,
            Map<String, String> environment,
            BiConsumer<String, String> onError)
    {
        ConfigurationInterpolator interpolator = new ConfigurationInterpolator(Map.of(
                "ENV", new ConfigurationInterpolator.EnvValueInterpolator(environment)));

        return interpolator.interpolate(properties, onError);
    }
}
