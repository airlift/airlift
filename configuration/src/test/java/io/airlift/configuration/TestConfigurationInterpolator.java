package io.airlift.configuration;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.airlift.configuration.ConfigurationUtils.replaceEnvironmentVariables;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

public class TestConfigurationInterpolator
{
    @Test
    public void testEnvironmentVariableReplacement()
    {
        Map<String, String> original = ImmutableMap.<String, String>builder()
                .put("apple", "apple-value")
                .put("grape", "${ENV:GRAPE}")
                .put("peach", "${ENV:PEACH}")
                .put("grass", "${ENV:!!!}")
                .put("pear", "${ENV:X_PEAR}")
                .put("cherry", "${ENV:X_CHERRY}")
                .put("orange", "orange-value")
                .put("watermelon", "${ENV:WATER}${ENV:MELON}")
                .put("blueberry", "${ENV:BLUE}${ENV:BERRY}")
                .put("contaminated-lemon", "${ENV:!!!}${ENV:LEMON}")
                .put("mixed-fruit", "mango-value:${ENV:BANANA}:${ENV:COCONUT}")
                .put("no-recursive-replacement", "${ENV:FIRST}, ${ENV:SECOND}")
                .build();

        Map<String, String> environment = ImmutableMap.<String, String>builder()
                .put("GRAPE", "env-grape")
                .put("X_CHERRY", "env-cherry")
                .put("WATER", "env-water")
                .put("MELON", "env-melon")
                .put("BERRY", "env-berry")
                .put("LEMON", "env-lemon")
                .put("BANANA", "env-banana")
                .put("COCONUT", "env-coconut")
                .put("FIRST", "env-first:${ENV:SECOND}:")
                .put("SECOND", "env-second:${ENV:FIRST}")
                .build();

        List<String> errors = new ArrayList<>();
        Map<String, String> actual = replaceEnvironmentVariables(original, environment, (key, error) -> errors.add(error));

        Map<String, String> expected = ImmutableMap.<String, String>builder()
                .put("apple", "apple-value")
                .put("grape", "env-grape")
                .put("grass", "${ENV:!!!}")
                .put("cherry", "env-cherry")
                .put("orange", "orange-value")
                .put("watermelon", "env-waterenv-melon")
                .put("contaminated-lemon", "${ENV:!!!}env-lemon")
                .put("mixed-fruit", "mango-value:env-banana:env-coconut")
                .put("no-recursive-replacement", "env-first:${ENV:SECOND}:, env-second:${ENV:FIRST}")
                .build();

        assertEquals(actual, expected);

        assertThat(errors).containsExactly(
                "Configuration property 'peach' references variable 'ENV:PEACH' that cannot be interpolated",
                "Configuration property 'pear' references variable 'ENV:X_PEAR' that cannot be interpolated",
                "Configuration property 'blueberry' references variable 'ENV:BLUE' that cannot be interpolated");
    }

    @Test
    public void testFileVariableReplacement()
            throws IOException
    {
        Path grapeFile = Files.createTempFile("", "");
        Path peachFile = Files.createTempFile("", "");

        Files.writeString(grapeFile, "file-grape");
        Files.writeString(peachFile, "file-peach");

        Map<String, String> original = ImmutableMap.<String, String>builder()
                .put("apple", "apple-value")
                .put("grape", "${FILE:%s}".formatted(grapeFile.toFile().getAbsolutePath()))
                .put("peach", "${FILE:%s}".formatted(peachFile.toFile().getAbsolutePath()))
                .put("blueberry", "${FILE:/missing/file}")
                .build();

        List<String> errors = new ArrayList<>();
        Map<String, String> actual = interpolate(original, Map.of(), (key, error) -> errors.add(error));

        Map<String, String> expected = ImmutableMap.<String, String>builder()
                .put("apple", "apple-value")
                .put("grape", "file-grape")
                .put("peach", "file-peach")
                .build();

        assertEquals(actual, expected);
        assertThat(errors).containsExactly("Configuration property 'blueberry' references variable 'FILE:/missing/file' that cannot be interpolated");
    }

    @Test
    public void testMissingInterpolator()
    {
        Map<String, String> original = ImmutableMap.<String, String>builder()
                .put("apple", "${SECRET:is-unknown}")
                .build();

        List<String> errors = new ArrayList<>();
        Map<String, String> actual = interpolate(original, Map.of(), (key, error) -> errors.add(error));
        Map<String, String> expected = Map.of();
        assertEquals(actual, expected);

        assertThat(errors).containsExactly(
                "Configuration property 'apple' references value interpolator 'SECRET' that does not exist");
    }

    public static Map<String, String> interpolate(Map<String, String> properties, Map<String, String> environment, BiConsumer<String, String> onError)
    {
        ConfigurationInterpolator interpolator = new ConfigurationInterpolator(Map.of(
                "ENV", new ConfigurationInterpolator.EnvValueInterpolator(environment),
                "FILE", new ConfigurationInterpolator.FileValueInterpolator()));

        return interpolator.interpolate(properties, onError);
    }
}
