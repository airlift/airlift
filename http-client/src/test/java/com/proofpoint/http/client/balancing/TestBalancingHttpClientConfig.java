package com.proofpoint.http.client.balancing;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.testing.ValidationAssertions;
import org.testng.annotations.Test;

import javax.validation.constraints.Min;
import java.util.Map;

import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;

public class TestBalancingHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(BalancingHttpClientConfig.class)
                .setMaxAttempts(3));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.max-attempts", "4")
                .build();

        BalancingHttpClientConfig expected = new BalancingHttpClientConfig()
                .setMaxAttempts(4);

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        ConfigAssertions.assertLegacyEquivalence(BalancingHttpClientConfig.class,
                ImmutableMap.<String, String>of());
    }

    @Test
    public void testMaxAttemptsBeanValidation()
    {
        assertValidates(new BalancingHttpClientConfig());
        assertValidates(new BalancingHttpClientConfig().setMaxAttempts(1));
        assertFailsValidation(new BalancingHttpClientConfig().setMaxAttempts(0), "maxAttempts", "must be greater than or equal to 1", Min.class);
    }
}
