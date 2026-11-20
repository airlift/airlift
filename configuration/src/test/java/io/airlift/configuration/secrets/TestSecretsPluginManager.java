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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.TomlConfiguration;
import io.airlift.spi.secrets.SecretProvider;
import io.airlift.spi.secrets.SecretProviderFactory;
import io.airlift.spi.secrets.SecretsPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.airlift.configuration.TomlConfiguration.createTomlConfiguration;
import static io.github.wasabithumb.jtoml.JToml.jToml;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestSecretsPluginManager
{
    @Test
    void testLoadsShareClassLoaderButNotFactories(@TempDir Path tempDir)
            throws Exception
    {
        Path secretsConfig = createSecretsConfig(tempDir);

        SecretsResolver first = loadSecretsResolver(secretsConfig);
        SecretsResolver second = loadSecretsResolver(secretsConfig);

        assertThat(factoryId(first)).isEqualTo("0");
        assertThat(factoryId(second))
                .as("second load shares the class loader, so the static counter continues")
                .isEqualTo("1");
        assertThat(first.resolveSecret("testing", "parallel-capable")).isEqualTo("true");
    }

    @Test
    void testClassLoaderIsCollectedWhenUnused(@TempDir Path tempDir)
            throws Exception
    {
        Path secretsConfig = createSecretsConfig(tempDir);
        assertThat(factoryId(loadSecretsResolver(secretsConfig))).isEqualTo("0");

        long deadline = System.nanoTime() + SECONDS.toNanos(30);
        String factoryId;
        do {
            System.gc();
            Thread.sleep(10);
            factoryId = factoryId(loadSecretsResolver(secretsConfig));
        }
        while (!factoryId.equals("0") && System.nanoTime() < deadline);

        assertThat(factoryId)
                .as("plugin class loader is collected and recreated once no bootstrap uses it")
                .isEqualTo("0");
    }

    @Test
    void testInvalidSecretProviderName()
            throws Exception
    {
        assertInvalidSecretProviderFactory("$invalid");
        assertInvalidSecretProviderFactory("1invalid");
        assertInvalidSecretProviderFactory("invAlid");
    }

    @Test
    void testLoadingWithUnknownConfigurationResolverName()
            throws Exception
    {
        Path configPluginDirectory = Files.createTempDirectory("config-plugins");
        SecretsPluginManager configurationPluginManager = new SecretsPluginManager(
                new TomlConfiguration(jToml().readFromString(
                        """
                        secrets-plugins-dir="%s"

                        [resolver-1]
                        secrets-provider.name="unknown"
                        """.formatted(configPluginDirectory.toAbsolutePath()))));

        assertThatThrownBy(configurationPluginManager::load)
                .hasMessageContaining("Secret provider 'unknown' is not registered");
    }

    @Test
    void testConfigurationResolutionWithoutEnvironmentVariableResolverConfigured()
            throws Exception
    {
        Path configPluginDirectory = Files.createTempDirectory("config-plugins");
        SecretsPluginManager configurationPluginManager = new SecretsPluginManager(
                new TomlConfiguration(jToml().readFromString(
                        """
                        secrets-plugins-dir="%s"
                        """.formatted(configPluginDirectory.toAbsolutePath()))));

        configurationPluginManager.installPlugins();
        configurationPluginManager.load();

        assertThatThrownBy(() -> configurationPluginManager.getSecretsResolver().getResolvedConfiguration(ImmutableMap.of("key", "${ENV:test}")))
                .hasMessageContaining("No secret provider for key 'env'");
    }

    private void assertInvalidSecretProviderFactory(String secretProviderName)
            throws Exception
    {
        Path configPluginDirectory = Files.createTempDirectory("config-plugins");
        SecretsPluginManager configurationPluginManager = new SecretsPluginManager(
                new TomlConfiguration(jToml().readFromString(
                        """
                        secrets-plugins-dir="%s"

                        [resolver-1]
                        secrets-provider.name="%s"
                        """.formatted(configPluginDirectory.toAbsolutePath(), secretProviderName))));

        assertThatThrownBy(() -> configurationPluginManager.installSecretsPlugin(() -> ImmutableList.of(new SecretProviderFactory()
        {
            @Override
            public String getName()
            {
                return secretProviderName;
            }

            @Override
            public SecretProvider createSecretProvider(Map<String, String> config)
            {
                return key -> key;
            }
        }))).hasMessageContaining("Secret provider name '%s' doesn't match pattern '[a-z][a-z0-9_-]*'".formatted(secretProviderName));
    }

    private static SecretsResolver loadSecretsResolver(Path secretsConfig)
    {
        SecretsPluginManager secretsPluginManager = new SecretsPluginManager(createTomlConfiguration(secretsConfig.toFile()));
        secretsPluginManager.installPlugins();
        secretsPluginManager.load();
        return secretsPluginManager.getSecretsResolver();
    }

    private static String factoryId(SecretsResolver secretsResolver)
    {
        return secretsResolver.resolveSecret("testing", "factory-id");
    }

    private static Path createSecretsConfig(Path tempDir)
            throws IOException
    {
        Path classes = tempDir.resolve("secrets-plugin/testing/classes");
        Path classFile = classes.resolve(TestingSecretsPlugin.class.getName().replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        try (InputStream input = TestingSecretsPlugin.class.getResourceAsStream(TestingSecretsPlugin.class.getSimpleName() + ".class")) {
            Files.copy(input, classFile);
        }
        Path services = Files.createDirectories(classes.resolve("META-INF/services"));
        Files.writeString(services.resolve(SecretsPlugin.class.getName()), TestingSecretsPlugin.class.getName());
        return Files.writeString(tempDir.resolve("secrets.toml"),
                """
                secrets-plugins-dir="%s"

                [testing]
                secrets-provider.name="testing"
                """.formatted(tempDir.resolve("secrets-plugin")));
    }
}
