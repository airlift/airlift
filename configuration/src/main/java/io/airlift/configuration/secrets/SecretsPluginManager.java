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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.TomlConfiguration;
import io.airlift.configuration.secrets.env.EnvironmentVariableSecretsPlugin;
import io.airlift.log.Logger;
import io.airlift.spi.secrets.SecretProvider;
import io.airlift.spi.secrets.SecretProviderFactory;
import io.airlift.spi.secrets.SecretsPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static io.airlift.configuration.ConfigurationUtils.replaceEnvironmentVariables;
import static java.nio.file.Files.newDirectoryStream;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public final class SecretsPluginManager
{
    private static final ImmutableList<String> SPI_PACKAGES = ImmutableList.<String>builder()
            .add("io.airlift.spi.secrets")
            .build();

    private static final Logger log = Logger.get(SecretsPluginManager.class);

    private static final String SECRETS_PROVIDER_NAME_PROPERTY = "secrets-provider.name";

    private static final Pattern SECRETS_PROVIDER_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_-]*");

    private final Map<String, SecretProviderFactory> secretsProviderFactories = new ConcurrentHashMap<>();
    private final AtomicReference<Map<String, SecretProvider>> secretsProviders = new AtomicReference<>(ImmutableMap.of());
    private final File installedSecretPluginsDir;
    private final TomlConfiguration tomlConfiguration;

    public SecretsPluginManager(TomlConfiguration tomlConfiguration)
    {
        this.tomlConfiguration = requireNonNull(tomlConfiguration, "tomlConfiguration is null");
        ConfigurationFactory configurationFactory = new ConfigurationFactory(tomlConfiguration.getParentConfiguration());
        SecretsPluginConfig config = configurationFactory.build(SecretsPluginConfig.class);
        this.installedSecretPluginsDir = config.getSecretsPluginsDir();
    }

    public void installPlugins()
    {
        installSecretsPlugin(new EnvironmentVariableSecretsPlugin());

        listFiles(installedSecretPluginsDir).stream()
                .filter(File::isDirectory)
                .forEach(file -> loadConfigurationResolvers(file.getAbsolutePath(), () -> createClassLoader(file.getName(), buildClassPath(file))));
    }

    @VisibleForTesting
    void installSecretsPlugin(SecretsPlugin secretsPlugin)
    {
        secretsPlugin.getSecretProviderFactories()
                .forEach(this::addSecretProviderFactory);
    }

    private void addSecretProviderFactory(SecretProviderFactory secretProviderFactory)
    {
        verify(SECRETS_PROVIDER_NAME_PATTERN.matcher(secretProviderFactory.getName()).matches(), "Secret provider name '%s' doesn't match pattern '%s'", secretProviderFactory.getName(), SECRETS_PROVIDER_NAME_PATTERN);
        secretsProviderFactories.put(
                secretProviderFactory.getName(),
                secretProviderFactory);
    }

    public void load()
    {
        ImmutableMap.Builder<String, SecretProvider> builder = ImmutableMap.builder();

        for (String namespace : tomlConfiguration.getNamespaces()) {
            Map<String, String> properties = new HashMap<>(tomlConfiguration.getNamespaceConfiguration(namespace));
            properties = replaceEnvironmentVariables(properties);
            String name = properties.remove(SECRETS_PROVIDER_NAME_PROPERTY);
            checkState(!isNullOrEmpty(name), "Configuration resolver configuration '%s' does not contain '%s'", namespace, SECRETS_PROVIDER_NAME_PROPERTY);
            builder.put(namespace, loadConfigProvider(name, properties));
        }
        this.secretsProviders.set(builder.buildOrThrow());
    }

    public SecretsResolver getSecretsResolver()
    {
        return new SecretsResolver(secretsProviders.get());
    }

    private void loadConfigurationResolvers(String plugin, Supplier<SecretsPluginClassLoader> createClassLoader)
    {
        log.info("-- Loading plugin %s --", plugin);

        SecretsPluginClassLoader pluginClassLoader = createClassLoader.get();

        log.debug("Classpath for plugin:");
        for (URL url : pluginClassLoader.getURLs()) {
            log.debug("    %s", url.getPath());
        }

        try (ThreadContextClassLoader ignored = new ThreadContextClassLoader(pluginClassLoader)) {
            loadConfigurationPlugin(pluginClassLoader);
        }

        log.info("-- Finished loading plugin %s --", plugin);
    }

    private void loadConfigurationPlugin(SecretsPluginClassLoader pluginClassLoader)
    {
        ServiceLoader<SecretsPlugin> serviceLoader = ServiceLoader.load(SecretsPlugin.class, pluginClassLoader);
        List<SecretsPlugin> plugins = ImmutableList.copyOf(serviceLoader);
        checkState(!plugins.isEmpty(), "No service providers of type %s in the classpath: %s", SecretsPlugin.class.getName(), asList(pluginClassLoader.getURLs()));

        for (SecretsPlugin plugin : plugins) {
            log.info("Installing %s", plugin.getClass().getName());
            installSecretsPlugin(plugin);
        }
    }

    private SecretProvider loadConfigProvider(String configProviderName, Map<String, String> properties)
    {
        log.info("-- Loading secret provider --");

        SecretProviderFactory factory = secretsProviderFactories.get(configProviderName);
        checkState(factory != null, "Secret provider '%s' is not registered", configProviderName);

        SecretProvider secretProvider;
        try (ThreadContextClassLoader ignored = new ThreadContextClassLoader(factory.getClass().getClassLoader())) {
            secretProvider = factory.createSecretProvider(ImmutableMap.copyOf(properties));
        }

        log.info("-- Loaded secret provider %s --", configProviderName);
        return secretProvider;
    }

    private static List<URL> buildClassPath(File path)
    {
        return listFiles(path).stream()
                .map(SecretsPluginManager::fileToUrl)
                .collect(toImmutableList());
    }

    private static SecretsPluginClassLoader createClassLoader(String pluginName, List<URL> urls)
    {
        ClassLoader parent = SecretsPluginManager.class.getClassLoader();
        return new SecretsPluginClassLoader(pluginName, urls, parent, SPI_PACKAGES);
    }

    private static List<File> listFiles(File path)
    {
        try {
            try (DirectoryStream<Path> directoryStream = newDirectoryStream(path.toPath())) {
                return stream(directoryStream)
                        .map(Path::toFile)
                        .sorted()
                        .collect(toImmutableList());
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static URL fileToUrl(File file)
    {
        try {
            return file.toURI().toURL();
        }
        catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }
}
