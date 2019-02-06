package io.airlift.json;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestJsonCodecConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(JsonCodecConfig.class)
                .setIncludeAfterBurnerModule(false));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("json.module.after-burner.enabled", "true")
                .build();

        JsonCodecConfig expected = new JsonCodecConfig()
                .setIncludeAfterBurnerModule(true);
        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
