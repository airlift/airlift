/*
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
package io.airlift.configuration.secrets;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.airlift.configuration.secrets.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.secrets.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.secrets.ConfigAssertions.recordDefaults;

final class TestSecretsPluginConfig
{
    @Test
    void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(SecretsPluginConfig.class)
                .setSecretsPluginsDir(new File("secrets-plugin")));
    }

    @Test
    void testExplicitPropertyMappings()
            throws Exception
    {
        Path configPluginDirectory = Files.createTempFile(null, null);

        Map<String, String> properties = ImmutableMap.of("secrets-plugins-dir", configPluginDirectory.toString());

        SecretsPluginConfig expected = new SecretsPluginConfig()
                .setSecretsPluginsDir(configPluginDirectory.toFile());

        assertFullMapping(properties, expected);
    }
}
