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
package com.proofpoint.bootstrap;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.spi.Message;
import com.proofpoint.bootstrap.LoggingWriter.Type;
import com.proofpoint.configuration.ConfigurationAwareModule;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationFactoryBuilder;
import com.proofpoint.configuration.ConfigurationInspector;
import com.proofpoint.configuration.ConfigurationInspector.ConfigAttribute;
import com.proofpoint.configuration.ConfigurationInspector.ConfigRecord;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.configuration.ConfigurationValidator;
import com.proofpoint.configuration.ValidationErrorModule;
import com.proofpoint.configuration.WarningsMonitor;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;
import com.proofpoint.node.ApplicationNameModule;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Entry point for an application built using the platform codebase.
 * <p/>
 * This class will:
 * <ul>
 *  <li>load, validate and bind configurations</li>
 *  <li>initialize logging</li>
 *  <li>set up bootstrap management</li>
 *  <li>create an Guice injector</li>
 * </ul>
 */
public class Bootstrap
{
    private final Logger log = Logger.get("Bootstrap");
    private final Logging logging;
    private final List<Module> modules;

    private Map<String, String> requiredConfigurationProperties = null;
    private Map<String, String> applicationDefaults = null;
    private boolean requireExplicitBindings = true;

    private boolean initialized = false;
    private final String applicationName;

    /**
     * @deprecated Use <tt>Bootstrap.bootstrapApplication(String).withModules(Module...)</tt> instead.
     */
    @Deprecated
    public Bootstrap(Module... modules)
    {
        this(ImmutableList.copyOf(modules));
    }

    /**
     * @deprecated Use <tt>Bootstrap.bootstrapApplication(String).withModules(Iterable &lt;? extends Module>&gt;)</tt> instead.
     */
    @Deprecated
    public Bootstrap(Iterable<? extends Module> modules)
    {
        this("legacy-app", modules, true);
    }

    public static BootstrapBeforeModules bootstrapApplication(String applicationName)
    {
        return new BootstrapBeforeModules(applicationName);
    }

    private Bootstrap(String applicationName, Iterable<? extends Module> modules, boolean initializeLogging)
    {
        if (initializeLogging) {
            logging = Logging.initialize();
        }
        else {
            logging = null;
        }

        Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread t, Throwable e)
            {
                log.error(e, "Uncaught exception in thread %s", t.getName());
            }
        });

        this.applicationName = checkNotNull(applicationName, "applicationName is null");
        this.modules = ImmutableList.copyOf(modules);
    }

    @Beta
    public Bootstrap setRequiredConfigurationProperty(String key, String value)
    {
        if (this.requiredConfigurationProperties == null) {
            this.requiredConfigurationProperties = new TreeMap<>();
        }
        this.requiredConfigurationProperties.put(key, value);
        return this;
    }

    @Beta
    public Bootstrap setRequiredConfigurationProperties(Map<String, String> requiredConfigurationProperties)
    {
        if (this.requiredConfigurationProperties == null) {
            this.requiredConfigurationProperties = new TreeMap<>();
        }
        this.requiredConfigurationProperties.putAll(requiredConfigurationProperties);
        return this;
    }

    public Bootstrap withApplicationDefaults(Map<String, String> applicationDefaults)
    {
        checkState(this.applicationDefaults == null, "applicationDefaults already specified");
        this.applicationDefaults = checkNotNull(applicationDefaults, "applicationDefaults is null");
        return this;
    }

    public Bootstrap requireExplicitBindings(boolean requireExplicitBindings)
    {
        this.requireExplicitBindings = requireExplicitBindings;
        return this;
    }

    public Injector initialize()
            throws Exception
    {
        checkState(!initialized, "Already initialized");
        initialized = true;

        // initialize configuration
        ConfigurationFactoryBuilder builder = new ConfigurationFactoryBuilder();
        if (applicationDefaults != null) {
            builder = builder.withApplicationDefaults(applicationDefaults);
        }
        if (requiredConfigurationProperties == null) {
            log.info("Loading configuration");
            builder = builder
                    .withFile(System.getProperty("config"))
                    .withSystemProperties();
        }
        else {
            builder = builder.withRequiredProperties(requiredConfigurationProperties);
        }
        ConfigurationFactory configurationFactory = builder.build();

        if (logging != null) {
            // initialize logging
            log.info("Initializing logging");
            LoggingConfiguration configuration = configurationFactory.build(LoggingConfiguration.class);
            logging.configure(configuration);
        }

        // create warning logger now that we have logging initialized
        final List<String> warnings = new ArrayList<>();
        final WarningsMonitor warningsMonitor = new WarningsMonitor()
        {
            @Override
            public void onWarning(String message)
            {
                log.warn(message);
                warnings.add(message);
            }
        };

        // initialize configuration factory
        for (Module module : modules) {
            if (module instanceof ConfigurationAwareModule) {
                ConfigurationAwareModule configurationAwareModule = (ConfigurationAwareModule) module;
                configurationAwareModule.setConfigurationFactory(configurationFactory);
            }
        }

        // Validate configuration
        ConfigurationValidator configurationValidator = new ConfigurationValidator(configurationFactory, warningsMonitor);
        List<Message> messages = configurationValidator.validate(modules);

        // Log effective configuration
        logConfiguration(configurationFactory);

        // system modules
        Builder<Module> moduleList = ImmutableList.builder();
        moduleList.add(new LifeCycleModule());
        moduleList.add(new ConfigurationModule(configurationFactory));
        moduleList.add(new ApplicationNameModule(applicationName));
        if (!messages.isEmpty()) {
            moduleList.add(new ValidationErrorModule(messages));
        }
        moduleList.add(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(WarningsMonitor.class).toInstance(warningsMonitor);
            }
        });

        moduleList.add(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.disableCircularProxies();
                if(requireExplicitBindings) {
                    binder.requireExplicitBindings();
                }
            }
        });
        moduleList.addAll(modules);

        // create the injector
        final Injector injector = Guice.createInjector(Stage.PRODUCTION, moduleList.build());

        // Create the life-cycle manager
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        // Start services
        if (lifeCycleManager.size() > 0) {
            lifeCycleManager.start();
        }

        return injector;
    }

    private static final String PROPERTY_NAME_COLUMN = "PROPERTY";
    private static final String DEFAULT_VALUE_COLUMN = "DEFAULT";
    private static final String CURRENT_VALUE_COLUMN = "RUNTIME";
    private static final String DESCRIPTION_COLUMN = "DESCRIPTION";

    private void logConfiguration(ConfigurationFactory configurationFactory)
    {
        ColumnPrinter columnPrinter = makePrinterForConfiguration(configurationFactory);

        try (PrintWriter out = new PrintWriter(new LoggingWriter(log, Type.INFO))) {
            columnPrinter.print(out);
        }
    }

    private static ColumnPrinter makePrinterForConfiguration(ConfigurationFactory configurationFactory)
    {
        ConfigurationInspector configurationInspector = new ConfigurationInspector();

        ColumnPrinter columnPrinter = new ColumnPrinter();

        columnPrinter.addColumn(PROPERTY_NAME_COLUMN);
        columnPrinter.addColumn(DEFAULT_VALUE_COLUMN);
        columnPrinter.addColumn(CURRENT_VALUE_COLUMN);
        columnPrinter.addColumn(DESCRIPTION_COLUMN);

        for (ConfigRecord<?> record : configurationInspector.inspect(configurationFactory)) {
            for (ConfigAttribute attribute : record.getAttributes()) {
                columnPrinter.addValue(PROPERTY_NAME_COLUMN, attribute.getPropertyName());
                columnPrinter.addValue(DEFAULT_VALUE_COLUMN, attribute.getDefaultValue());
                columnPrinter.addValue(CURRENT_VALUE_COLUMN, attribute.getCurrentValue());
                columnPrinter.addValue(DESCRIPTION_COLUMN, attribute.getDescription());
            }
        }
        return columnPrinter;
    }

    public static class BootstrapBeforeModules
    {
        private final String applicationName;
        private boolean initializeLogging = true;

        private BootstrapBeforeModules(String applicationName)
        {
            this.applicationName = checkNotNull(applicationName, "applicationName is null");
        }

        @Beta
        public BootstrapBeforeModules doNotInitializeLogging()
        {
            this.initializeLogging = false;
            return this;
        }

        public Bootstrap withModules(Module... modules)
        {
            return withModules(ImmutableList.copyOf(modules));
        }

        public Bootstrap withModules(Iterable<? extends Module> modules)
        {
            return new Bootstrap(applicationName, modules, initializeLogging);
        }
    }
}
