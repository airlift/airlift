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
package com.proofpoint.rack;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.testing.ValidationAssertions;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;

public class TestRackServletConfig
{

    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(RackServletConfig.class)
                .setRackConfigPath("rack/config.ru")
                .setServiceAnnouncement(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("rackserver.rack-config-path", "rack-configuration.ru")
                .put("rackserver.announcement", "test")
                .build();

        RackServletConfig expected = new RackServletConfig()
                .setRackConfigPath("rack-configuration.ru")
                .setServiceAnnouncement("test");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        assertLegacyEquivalence(RackServletConfig.class, ImmutableMap.<String, String>of());
    }

    @Test
    public void testValidConfig()
    {
        RackServletConfig config = new RackServletConfig().setRackConfigPath(null);
        ValidationAssertions.assertFailsValidation(config, "rackConfigPath", "may not be null", NotNull.class);

    }
}
