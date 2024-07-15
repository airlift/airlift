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
package io.airlift.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestTomlConfiguration
{
    @Test
    void testTomlConfigurationWithInvalidExtension()
    {
        assertThatThrownBy(() -> TomlConfiguration.createTomlConfiguration(new File("configuration.properties")));
        assertThatThrownBy(() -> TomlConfiguration.createTomlConfiguration(new File("configuration.tom")));
    }

    @Test
    void testTomlParentConfiguration()
    {
        TomlConfiguration tomlConfiguration = TomlConfiguration.createTomlConfiguration(new File(Resources.getResource("configuration.toml").getPath()));
        assertThat(tomlConfiguration.getParentConfiguration()).isEqualTo(ImmutableMap.of("title", "TOML Example"));
    }

    @Test
    void testTomlNamespace()
    {
        TomlConfiguration tomlConfiguration = TomlConfiguration.createTomlConfiguration(new File(Resources.getResource("configuration.toml").getPath()));
        assertThat(tomlConfiguration.getNamespaces()).isEqualTo(ImmutableSet.of("owner", "database", "servers"));
    }

    @Test
    void testTomlNamespaceConfiguration()
    {
        TomlConfiguration tomlConfiguration = TomlConfiguration.createTomlConfiguration(new File(Resources.getResource("configuration.toml").getPath()));
        assertThat(tomlConfiguration.getNamespaceConfiguration("servers")).isEqualTo(ImmutableMap.of(
                "alpha.ip", "10.0.0.1",
                "alpha.role", "frontend",
                "beta.ip", "10.0.0.2",
                "beta.role", "backend",
                "beta.secret", "${ENV:SECRET_VALUE}"));
    }

    @Test
    void testTomlWithInvalidNamespace()
    {
        TomlConfiguration tomlConfiguration = TomlConfiguration.createTomlConfiguration(new File(Resources.getResource("configuration.toml").getPath()));
        assertThatThrownBy(() -> tomlConfiguration.getNamespaceConfiguration("invalid"))
                .hasMessageContaining("Namespace invalid not found");
    }
}
