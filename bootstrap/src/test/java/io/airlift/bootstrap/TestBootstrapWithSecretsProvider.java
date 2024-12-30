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
package io.airlift.bootstrap;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import io.airlift.configuration.Config;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.nio.file.Files.newBufferedWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestBootstrapWithSecretsProvider
{
    @Test
    void testBootstrapWithDefaultSecretsProvider()
    {
        Bootstrap bootstrap = new Bootstrap(binder -> configBinder(binder).bindConfig(FooConfig.class))
                .setRequiredConfigurationProperties(ImmutableMap.of("foo.value", "${ENV:TEST_KEY}"));

        Injector injector = bootstrap.initialize();

        assertThat(injector.getInstance(FooConfig.class).getValue()).isEqualTo("test_value");
    }

    @Test
    void testBootstrapWithEnvironmentSecretsProviderDisabled()
            throws Exception
    {
        Path configurationPluginDirectory = Files.createTempDirectory(null);

        File configurationResolverFile = createConfigurationResolverFile("secrets-plugins-dir=\"%s\"".formatted(configurationPluginDirectory));

        System.setProperty("secretsConfig", configurationResolverFile.getAbsolutePath());

        Bootstrap bootstrap = new Bootstrap(binder -> configBinder(binder).bindConfig(FooConfig.class))
                .loadSecretsPlugins()
                .setRequiredConfigurationProperties(ImmutableMap.of("foo.value", "${ENV:TEST_KEY}"));

        assertThatThrownBy(() -> bootstrap.initialize())
                .hasMessageContaining("No secret provider for key 'env'");
    }

    @Test
    void testBootstrapWithEnvironmentSecretsProviderEnabled()
            throws Exception
    {
        Path configurationPluginDirectory = Files.createTempDirectory(null);

        File configurationResolverFile = createConfigurationResolverFile("""
                secrets-plugins-dir="%s
                
                [env]
                secrets-provider.name="env"
                """.formatted(configurationPluginDirectory));

        System.setProperty("secretsConfig", configurationResolverFile.getAbsolutePath());

        Bootstrap bootstrap = new Bootstrap(binder -> configBinder(binder).bindConfig(FooConfig.class))
                .loadSecretsPlugins()
                .setRequiredConfigurationProperties(ImmutableMap.of("foo.value", "${ENV:TEST_KEY}"));

        Injector injector = bootstrap.initialize();

        assertThat(injector.getInstance(FooConfig.class).getValue()).isEqualTo("test_value");
    }

    @Test
    void testBootstrapWithEnvironmentSecretsProviderWithDifferentNamespace()
            throws Exception
    {
        Path configurationPluginDirectory = Files.createTempDirectory(null);

        File configurationResolverFile = createConfigurationResolverFile("""
                secrets-plugins-dir="%s
                
                [multi]
                secrets-provider.name="env"
                """.formatted(configurationPluginDirectory));

        System.setProperty("secretsConfig", configurationResolverFile.getAbsolutePath());

        Bootstrap bootstrap = new Bootstrap(binder -> configBinder(binder).bindConfig(FooConfig.class))
                .loadSecretsPlugins()
                .setRequiredConfigurationProperties(ImmutableMap.of("foo.value", "${MULTI:TEST_KEY}"));

        Injector injector = bootstrap.initialize();

        assertThat(injector.getInstance(FooConfig.class).getValue()).isEqualTo("test_value");
    }

    private File createConfigurationResolverFile(String configurationFile)
            throws Exception
    {
        File tomlFile = File.createTempFile("config_resolver", ".toml");
        tomlFile.deleteOnExit();

        try (BufferedWriter writer = newBufferedWriter(tomlFile.toPath())) {
            writer.append(configurationFile);
        }

        return tomlFile;
    }

    public static class FooConfig
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("foo.value")
        public FooConfig setValue(String value)
        {
            this.value = value;
            return this;
        }
    }
}
