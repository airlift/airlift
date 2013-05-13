/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.platform.sample;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import com.proofpoint.units.MinDuration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;

public class TestStoreConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(StoreConfig.class)
                .setTtl(new Duration(1, TimeUnit.HOURS)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("store.ttl", "2h")
                .build();

        StoreConfig expected = new StoreConfig()
                .setTtl(new Duration(2, TimeUnit.HOURS));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("store.ttl", "1h")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("store.ttl-in-ms", "3600000")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(StoreConfig.class, currentProperties, oldProperties);
    }

    @Test
    public void testMinTtl() {
        assertValidates(new StoreConfig().setTtl(new Duration(1, TimeUnit.MINUTES)));
        assertFailsValidation(new StoreConfig().setTtl(new Duration(59, TimeUnit.SECONDS)),
                "ttl", "must be at least 1m", MinDuration.class);
    }
}
