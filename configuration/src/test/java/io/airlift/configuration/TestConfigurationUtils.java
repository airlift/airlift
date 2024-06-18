package io.airlift.configuration;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.airlift.configuration.ConfigurationUtils.replaceEnvironmentVariables;
import static org.assertj.core.api.Assertions.assertThat;

public class TestConfigurationUtils
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

        assertThat(actual).isEqualTo(expected);

        assertThat(errors).containsExactly(
                "Configuration property 'peach' references unset environment variable 'PEACH'",
                "Configuration property 'pear' references unset environment variable 'X_PEAR'",
                "Configuration property 'blueberry' references unset environment variable 'BLUE'");
    }
}
