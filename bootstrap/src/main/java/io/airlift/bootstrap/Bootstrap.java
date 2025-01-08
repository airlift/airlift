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
package io.airlift.bootstrap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.spi.Message;
import io.airlift.configuration.ConfigPropertyMetadata;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationInspector;
import io.airlift.configuration.ConfigurationInspector.ConfigAttribute;
import io.airlift.configuration.ConfigurationInspector.ConfigRecord;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.configuration.WarningsMonitor;
import io.airlift.configuration.secrets.SecretsPluginManager;
import io.airlift.configuration.secrets.SecretsResolver;
import io.airlift.configuration.secrets.env.EnvironmentVariableSecretProvider;
import io.airlift.log.Logger;
import io.airlift.log.Logging;
import io.airlift.log.LoggingConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.configuration.ConfigurationLoader.getSystemProperties;
import static io.airlift.configuration.ConfigurationLoader.loadPropertiesFrom;
import static io.airlift.configuration.TomlConfiguration.createTomlConfiguration;
import static java.lang.String.format;

/**
 * Entry point for an application built using the platform codebase.
 * <p>
 * This class will:
 * <ul>
 * <li>load, validate and bind configurations</li>
 * <li>initialize logging</li>
 * <li>set up bootstrap management</li>
 * <li>create an Guice injector</li>
 * </ul>
 */
public class Bootstrap
{
    private enum State { UNINITIALIZED, CONFIGURED, INITIALIZED }

    private final Logger log = Logger.get("Bootstrap");
    private final List<Module> modules;

    private Map<String, String> requiredConfigurationProperties;
    private Map<String, String> optionalConfigurationProperties;
    private boolean initializeLogging = true;
    private boolean quiet;
    private boolean loadSecretsPlugins;
    private boolean skipErrorReporting;

    private State state = State.UNINITIALIZED;
    private ConfigurationFactory configurationFactory;
    private SecretsResolver secretsResolver;

    public Bootstrap(Module... modules)
    {
        this(ImmutableList.copyOf(modules));
    }

    public Bootstrap(Iterable<? extends Module> modules)
    {
        this.modules = ImmutableList.copyOf(modules);
    }

    public Bootstrap setRequiredConfigurationProperty(String key, String value)
    {
        if (this.requiredConfigurationProperties == null) {
            this.requiredConfigurationProperties = new TreeMap<>();
        }
        this.requiredConfigurationProperties.put(key, value);
        return this;
    }

    public Bootstrap setRequiredConfigurationProperties(Map<String, String> requiredConfigurationProperties)
    {
        if (this.requiredConfigurationProperties == null) {
            this.requiredConfigurationProperties = new TreeMap<>();
        }
        this.requiredConfigurationProperties.putAll(requiredConfigurationProperties);
        return this;
    }

    public Bootstrap setOptionalConfigurationProperty(String key, String value)
    {
        if (this.optionalConfigurationProperties == null) {
            this.optionalConfigurationProperties = new TreeMap<>();
        }
        this.optionalConfigurationProperties.put(key, value);
        return this;
    }

    public Bootstrap setOptionalConfigurationProperties(Map<String, String> optionalConfigurationProperties)
    {
        if (this.optionalConfigurationProperties == null) {
            this.optionalConfigurationProperties = new TreeMap<>();
        }
        this.optionalConfigurationProperties.putAll(optionalConfigurationProperties);
        return this;
    }

    public Bootstrap doNotInitializeLogging()
    {
        this.initializeLogging = false;
        return this;
    }

    public Bootstrap quiet()
    {
        this.quiet = true;
        return this;
    }

    public Bootstrap loadSecretsPlugins()
    {
        this.loadSecretsPlugins = true;
        return this;
    }

    public Bootstrap skipErrorReporting()
    {
        this.skipErrorReporting = true;
        return this;
    }

    /**
     * Validate configuration and return used properties.
     */
    public Set<ConfigPropertyMetadata> configure()
    {
        checkState(state == State.UNINITIALIZED, "Already configured");
        state = State.CONFIGURED;

        Logging logging = null;
        if (initializeLogging) {
            logging = Logging.initialize();
        }

        this.secretsResolver = new SecretsResolver(ImmutableMap.of("env", new EnvironmentVariableSecretProvider()));

        if (loadSecretsPlugins) {
            log.info("Loading secrets plugins");
            String secretsConfigFile = System.getProperty("secretsConfig");
            if (secretsConfigFile != null) {
                SecretsPluginManager secretsPluginManager = new SecretsPluginManager(createTomlConfiguration(new File(secretsConfigFile)));
                secretsPluginManager.installPlugins();
                secretsPluginManager.load();
                this.secretsResolver = secretsPluginManager.getSecretsResolver();
            }
        }

        Map<String, String> requiredProperties;
        if (requiredConfigurationProperties == null) {
            // initialize configuration
            log.info("Loading configuration");

            requiredProperties = Collections.emptyMap();
            String configFile = System.getProperty("config");
            if (configFile != null) {
                try {
                    requiredProperties = loadPropertiesFrom(configFile);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        else {
            requiredProperties = requiredConfigurationProperties;
        }
        Map<String, String> unusedProperties = new TreeMap<>(requiredProperties);

        // combine property sources
        Map<String, String> properties = new HashMap<>();
        if (optionalConfigurationProperties != null) {
            properties.putAll(optionalConfigurationProperties);
        }
        properties.putAll(requiredProperties);
        properties.putAll(getSystemProperties());

        // replace secrets in property values
        List<Message> errors = new ArrayList<>();
        properties = ImmutableSortedMap.copyOf(secretsResolver.getResolvedConfiguration(properties, (key, error) -> {
            unusedProperties.remove(key);
            errors.add(new Message(error.getMessage()));
        }));

        List<Message> warnings = new ArrayList<>();
        configurationFactory = new ConfigurationFactory(properties, warning -> warnings.add(new Message(warning)));

        Boolean quietConfig = configurationFactory.build(BootstrapConfig.class).isQuiet();

        // initialize logging
        if (logging != null) {
            log.info("Initializing logging");
            LoggingConfiguration configuration = configurationFactory.build(LoggingConfiguration.class);
            logging.configure(configuration);
        }

        // Register configuration classes defined in the modules
        errors.addAll(configurationFactory.registerConfigurationClasses(modules));

        // Validate configuration classes
        errors.addAll(configurationFactory.validateRegisteredConfigurationProvider());

        // at this point all config file properties should be used
        // so we can calculate the unused properties
        Set<String> usedProperties = configurationFactory.getUsedProperties().stream()
                .map(ConfigPropertyMetadata::name)
                .collect(toImmutableSet());
        unusedProperties.keySet().removeAll(usedProperties);

        for (String key : unusedProperties.keySet()) {
            errors.add(new Message(format("Configuration property '%s' was not used", key)));
        }

        // If there are configuration errors, fail-fast to keep output clean
        if (!skipErrorReporting && !errors.isEmpty()) {
            throw new ApplicationConfigurationException(errors, warnings);
        }

        // Log effective configuration
        if (!((quietConfig == null) ? quiet : quietConfig)) {
            logConfiguration(configurationFactory);
        }

        // Log any warnings
        if (!skipErrorReporting && !warnings.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Configuration warnings\n");
            message.append("==========\n\n");
            message.append("Configuration should be updated:\n\n");
            for (int index = 0; index < warnings.size(); index++) {
                message.append(format("%s) %s\n", index + 1, warnings.get(index)));
            }
            message.append("\n");
            message.append("==========");
            log.warn(message.toString());
        }

        return configurationFactory.getUsedProperties();
    }

    public Injector initialize()
    {
        checkState(state != State.INITIALIZED, "Already initialized");
        if (state == State.UNINITIALIZED) {
            configure();
        }
        state = State.INITIALIZED;

        // system modules
        Builder<Module> moduleList = ImmutableList.builder();
        moduleList.add(new LifeCycleModule());
        moduleList.add(new ConfigurationModule(configurationFactory));
        moduleList.add(binder -> binder.bind(WarningsMonitor.class).toInstance(log::warn));

        // disable broken Guice "features"
        moduleList.add(Binder::disableCircularProxies);
        moduleList.add(Binder::requireExplicitBindings);
        moduleList.add(Binder::requireExactBindingAnnotations);

        moduleList.add(binder -> binder.bind(SecretsResolver.class).toInstance(secretsResolver));
        moduleList.addAll(modules);

        // create the injector
        Injector injector = Guice.createInjector(Stage.PRODUCTION, moduleList.build());

        injector.getInstance(LifeCycleManager.class).start();

        return injector;
    }

    private void logConfiguration(ConfigurationFactory configurationFactory)
    {
        if (!log.isInfoEnabled()) {
            return;
        }

        ColumnPrinter columnPrinter = makePrinterForConfiguration(configurationFactory);
        for (String line : columnPrinter.generateOutput()) {
            log.info(line);
        }
    }

    private static ColumnPrinter makePrinterForConfiguration(ConfigurationFactory configurationFactory)
    {
        ConfigurationInspector configurationInspector = new ConfigurationInspector();

        ColumnPrinter columnPrinter = new ColumnPrinter(
                "PROPERTY", "DEFAULT", "RUNTIME", "DESCRIPTION");

        for (ConfigRecord<?> record : configurationInspector.inspect(configurationFactory)) {
            for (ConfigAttribute attribute : record.getAttributes()) {
                columnPrinter.addValues(
                        attribute.getPropertyName(),
                        attribute.getDefaultValue(),
                        attribute.getCurrentValue(),
                        attribute.getDescription());
            }
        }
        return columnPrinter;
    }
}
