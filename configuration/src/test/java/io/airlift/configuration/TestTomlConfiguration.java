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

import static io.github.wasabithumb.jtoml.JToml.jToml;
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
    void testTomlNamespaceWithEmptyTables()
    {
        TomlConfiguration tomlConfiguration = new TomlConfiguration(jToml().readFromString(
                """
                title = "TOML Example"

                [empty]

                [parent.child]
                value = 1

                [[servers]]
                ip = "10.0.0.1"
                """));
        assertThat(tomlConfiguration.getNamespaces()).isEqualTo(ImmutableSet.of("empty", "parent"));
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
    void testTomlNamespaceConfigurationWithNestedValues()
    {
        TomlConfiguration tomlConfiguration = TomlConfiguration.createTomlConfiguration(
                new File(Resources.getResource("configuration.toml").getPath()));
        assertThat(tomlConfiguration.getNamespaceConfiguration("database")).isEqualTo(ImmutableMap.of(
                "data", "delta,phi,3.14",
                "enabled", "true",
                "ports", "8000,8001,8002",
                "temp_targets.case", "72.0",
                "temp_targets.cpu", "79.5"));
    }

    @Test
    void testTomlWithInvalidNamespace()
    {
        TomlConfiguration tomlConfiguration = TomlConfiguration.createTomlConfiguration(new File(Resources.getResource("configuration.toml").getPath()));
        assertThatThrownBy(() -> tomlConfiguration.getNamespaceConfiguration("invalid"))
                .hasMessageContaining("Namespace invalid not found");
    }

    @Test
    void testTomlWithNonTableNamespace()
    {
        TomlConfiguration tomlConfiguration = new TomlConfiguration(jToml().readFromString("invalid = true"));
        assertThatThrownBy(() -> tomlConfiguration.getNamespaceConfiguration("invalid"))
                .hasMessageContaining("Namespace invalid is not a TOML table");
    }
}
