package io.airlift.configuration;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Verify.verify;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static java.util.regex.Matcher.quoteReplacement;

public class ConfigurationInterpolator
{
    private static final Pattern PATTERN = Pattern.compile("\\$\\{(?<name>[A-Z]+):(?<key>[a-zA-Z0-9_\\-/]*)}");
    private final Map<String, ValueInterpolator> valueProviders;

    public ConfigurationInterpolator(Map<String, ValueInterpolator> valueProviders)
    {
        this.valueProviders = ImmutableMap.copyOf(requireNonNull(valueProviders, "valueProviders is null"));
        verifyValueProviderNames(valueProviders.keySet());
    }

    private static void verifyValueProviderNames(Set<String> names)
    {
        for (String name : names) {
            verify(name.toUpperCase(ENGLISH).contentEquals(name), "ValueInterpolator name '%s' is not uppercase", name);
        }
    }

    public Map<String, String> interpolate(Map<String, String> properties, BiConsumer<String, String> onError)
    {
        Map<String, String> replaced = new HashMap<>();
        properties.forEach((propertyKey, propertyValue) -> {
            StringBuilder replacedPropertyValue = new StringBuilder();
            Matcher matcher = PATTERN.matcher(propertyValue);
            while (matcher.find()) {
                String interpolatorName = matcher.group("name");
                String keyName = matcher.group("key");
                ValueInterpolator valueInterpolator = valueProviders.get(interpolatorName);
                if (valueInterpolator == null) {
                    onError.accept(propertyKey, "Configuration property '%s' references value interpolator '%s' that does not exist".formatted(propertyKey, interpolatorName));
                    return;
                }

                Optional<String> replacedValue = valueInterpolator.interpolate(keyName);
                if (replacedValue.orElse(null) == null) {
                    onError.accept(propertyKey, "Configuration property '%s' references variable '%s:%s' that cannot be interpolated".formatted(propertyKey, interpolatorName, keyName));
                    return;
                }
                matcher.appendReplacement(replacedPropertyValue, quoteReplacement(replacedValue.get()));
            }
            matcher.appendTail(replacedPropertyValue);
            replaced.put(propertyKey, replacedPropertyValue.toString());
        });
        return replaced;
    }

    public interface ValueInterpolator
    {
        Optional<String> interpolate(String key);
    }

    public static class EnvValueInterpolator
            implements ValueInterpolator
    {
        private final Map<String, String> envValues;

        public EnvValueInterpolator(Map<String, String> envValues)
        {
            this.envValues = ImmutableMap.copyOf(requireNonNull(envValues, "envValues is null"));
        }

        @Override
        public Optional<String> interpolate(String key)
        {
            return Optional.ofNullable(envValues.get(key));
        }
    }

    public static class FileValueInterpolator
            implements ValueInterpolator
    {
        @Override
        public Optional<String> interpolate(String key)
        {
            Path valueFile = Paths.get(key);
            if (!Files.exists(valueFile)) {
                return Optional.empty();
            }

            try {
                byte[] bytes = Files.readAllBytes(valueFile);
                if (bytes.length == 0) {
                    return Optional.empty();
                }
                return Optional.of(new String(bytes, StandardCharsets.UTF_8));
            }
            catch (IOException e) {
                return Optional.empty();
            }
        }
    }
}
