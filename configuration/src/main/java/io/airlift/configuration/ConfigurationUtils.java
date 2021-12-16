package io.airlift.configuration;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.regex.Matcher.quoteReplacement;

public class ConfigurationUtils
{
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{ENV:([a-zA-Z][a-zA-Z0-9_]*)}");

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
        Map<String, String> replaced = new HashMap<>();
        properties.forEach((propertyKey, propertyValue) -> {
            StringBuilder replacedPropertyValue = new StringBuilder();
            Matcher matcher = ENV_PATTERN.matcher(propertyValue);
            while (matcher.find()) {
                String envName = matcher.group(1);
                String envValue = environment.get(envName);
                if (envValue == null) {
                    onError.accept(propertyKey, format("Configuration property '%s' references unset environment variable '%s'", propertyKey, envName));
                    return;
                }
                matcher.appendReplacement(replacedPropertyValue, quoteReplacement(envValue));
            }
            matcher.appendTail(replacedPropertyValue);
            replaced.put(propertyKey, replacedPropertyValue.toString());
        });
        return replaced;
    }
}
