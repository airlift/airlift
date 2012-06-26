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
package io.airlift.event.client;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestHttpEventClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpEventClientConfig.class)
                .setJsonVersion(2)
                .setMaxConnections(-1)
                .setConnectTimeout(new Duration(50, TimeUnit.MILLISECONDS))
                .setRequestTimeout(new Duration(60, TimeUnit.SECONDS))
                .setCompress(false)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("collector.json-version", "1")
                .put("collector.max-connections", "10")
                .put("collector.connect-timeout", "3s")
                .put("collector.request-timeout", "8s")
                .put("collector.compress", "true")
                .build();

        HttpEventClientConfig expected = new HttpEventClientConfig()
                .setJsonVersion(1)
                .setMaxConnections(10)
                .setConnectTimeout(new Duration(3, TimeUnit.SECONDS))
                .setRequestTimeout(new Duration(8, TimeUnit.SECONDS))
                .setCompress(true);

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
