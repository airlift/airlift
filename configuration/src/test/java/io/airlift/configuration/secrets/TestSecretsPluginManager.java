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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.TomlConfiguration;
import io.airlift.spi.secrets.SecretProvider;
import io.airlift.spi.secrets.SecretProviderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

final class TestSecretsPluginManager {
    @Test
    void testInvalidSecretProviderName() throws Exception {
        assertInvalidSecretProviderFactory("$invalid");
        assertInvalidSecretProviderFactory("1invalid");
        assertInvalidSecretProviderFactory("invAlid");
    }

    @Test
    void testLoadingWithUnknownConfigurationResolverName() throws Exception {
        Path configPluginDirectory = Files.createTempDirectory("config-plugins");
        SecretsPluginManager configurationPluginManager = new SecretsPluginManager(
                new TomlConfiguration(Toml.parse("""
                secrets-plugins-dir="%s"

                [resolver-1]
                secrets-provider.name="unknown"
                """.formatted(configPluginDirectory.toAbsolutePath()))));

        assertThatThrownBy(configurationPluginManager::load)
                .hasMessageContaining("Secret provider 'unknown' is not registered");
    }

    @Test
    void testConfigurationResolutionWithoutEnvironmentVariableResolverConfigured() throws Exception {
        Path configPluginDirectory = Files.createTempDirectory("config-plugins");
        SecretsPluginManager configurationPluginManager = new SecretsPluginManager(
                new TomlConfiguration(Toml.parse("""
                secrets-plugins-dir="%s"
                """.formatted(configPluginDirectory.toAbsolutePath()))));

        configurationPluginManager.installPlugins();
        configurationPluginManager.load();

        assertThatThrownBy(() -> configurationPluginManager
                        .getSecretsResolver()
                        .getResolvedConfiguration(ImmutableMap.of("key", "${ENV:test}")))
                .hasMessageContaining("No secret provider for key 'env'");
    }

    private void assertInvalidSecretProviderFactory(String secretProviderName) throws Exception {
        Path configPluginDirectory = Files.createTempDirectory("config-plugins");
        SecretsPluginManager configurationPluginManager = new SecretsPluginManager(new TomlConfiguration(
                Toml.parse("""
                secrets-plugins-dir="%s"

                [resolver-1]
                secrets-provider.name="%s"
                """.formatted(configPluginDirectory.toAbsolutePath(), secretProviderName))));

        assertThatThrownBy(() -> configurationPluginManager.installSecretsPlugin(
                        () -> ImmutableList.of(new SecretProviderFactory() {
                            @Override
                            public String getName() {
                                return secretProviderName;
                            }

                            @Override
                            public SecretProvider createSecretProvider(Map<String, String> config) {
                                return key -> key;
                            }
                        })))
                .hasMessageContaining("Secret provider name '%s' doesn't match pattern '[a-z][a-z0-9_-]*'"
                        .formatted(secretProviderName));
    }
}
