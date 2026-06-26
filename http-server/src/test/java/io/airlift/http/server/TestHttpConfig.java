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
package io.airlift.http.server;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertDeprecatedEquivalence;
import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

public class TestHttpConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(HttpConfig.class)
                .setHttpPort(8080)
                .setAcceptQueueSize(8000)
                .setHttpAcceptorThreads(null)
                .setHttpSelectorThreads(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.port", "1")
                .put("http-server.http.accept-queue-size", "1024")
                .put("http-server.http.acceptor-threads", "10")
                .put("http-server.http.selector-threads", "11")
                .build();

        HttpConfig expected = new HttpConfig()
                .setHttpPort(1)
                .setAcceptQueueSize(1024)
                .setHttpAcceptorThreads(10)
                .setHttpSelectorThreads(11);

        assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyPropertyMapping()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.accept-queue-size", "1024")
                .build();

        Map<String, String> legacyProperties = new ImmutableMap.Builder<String, String>()
                .put("http-server.accept-queue-size", "1024")
                .build();

        assertDeprecatedEquivalence(HttpConfig.class, currentProperties, legacyProperties);
    }
}
