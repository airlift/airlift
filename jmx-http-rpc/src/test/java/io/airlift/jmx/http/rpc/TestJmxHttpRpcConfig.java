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
package io.airlift.jmx.http.rpc;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class TestJmxHttpRpcConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(JmxHttpRpcConfig.class)
                .setUsername(null)
                .setPassword(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jmx-http-rpc.username", "user")
                .put("jmx-http-rpc.password", "pass")
                .build();

        JmxHttpRpcConfig expected = new JmxHttpRpcConfig()
                .setUsername("user")
                .setPassword("pass");

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
