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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.spi.Message;
import com.proofpoint.bootstrap.LoggingWriter.Type;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationInspector;
import com.proofpoint.configuration.ConfigurationInspector.ConfigAttribute;
import com.proofpoint.configuration.ConfigurationInspector.ConfigRecord;
import com.proofpoint.configuration.ConfigurationLoader;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.configuration.ConfigurationValidator;
import com.proofpoint.configuration.ValidationErrorModule;
import com.proofpoint.configuration.WarningsMonitor;
import com.proofpoint.jmx.JmxInspector;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

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
    private final Logger log = Logger.get(Bootstrap.class);
    private final Module[] modules;
    private boolean strictConfig = false;

    public Bootstrap(Module... modules)
    {
        this.modules = modules;
    }

    public Bootstrap strictConfig()
    {
        this.strictConfig = true;
        return this;
    }

    public Injector initialize()
            throws Exception
    {
        Logging logging = new Logging();

        Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread t, Throwable e)
            {
                log.error(e, "Uncaught exception in thread %s", t.getName());
            }
        });

        // initialize configuration
        log.info("Loading configuration");
        ConfigurationLoader loader = new ConfigurationLoader();

        Map<String, String> configFileProperties = Collections.emptyMap();
        String configFile = System.getProperty("config");
        if (configFile != null) {
            configFileProperties = loader.loadPropertiesFrom(configFile);
        }

        SortedMap<String, String> properties = Maps.newTreeMap();
        properties.putAll(configFileProperties);
        properties.putAll(loader.getSystemProperties());
        properties = ImmutableSortedMap.copyOf(properties);

        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);

        // initialize logging
        log.info("Initializing logging");
        LoggingConfiguration configuration = configurationFactory.build(LoggingConfiguration.class);
        logging.initialize(configuration);

        // create warning logger now that we have logging initialized
        final WarningsMonitor warningsMonitor = new WarningsMonitor()
        {
            @Override
            public void onWarning(String message)
            {
                log.warn(message);
            }
        };

        // Validate configuration
        ConfigurationValidator configurationValidator = new ConfigurationValidator(configurationFactory, warningsMonitor);
        List<Message> messages = configurationValidator.validate(modules);

        // at this point all config file properties should be used
        // so we can calculate the unused properties
        final TreeMap<String, String> unusedProperties = Maps.newTreeMap();
        unusedProperties.putAll(configFileProperties);
        unusedProperties.keySet().removeAll(configurationFactory.getUsedProperties());

        // system modules
        Builder<Module> moduleList = ImmutableList.builder();
        moduleList.add(new LifeCycleModule());
        moduleList.add(new ConfigurationModule(configurationFactory));
        if (!messages.isEmpty()) {
            moduleList.add(new ValidationErrorModule(messages));
        }
        moduleList.add(new Module() {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(WarningsMonitor.class).toInstance(warningsMonitor);
            }
        });
        // todo this should be part of the ValidationErrorModule
        if (strictConfig) {
            moduleList.add(new Module()
            {
                @Override
                public void configure(Binder binder)
                {
                    for (Entry<String,String> unusedProperty : unusedProperties.entrySet()) {
                        binder.addError("Configuration property '%s=%s' was not used", unusedProperty.getKey(), unusedProperty.getValue());
                    }
                }
            });
        }
        moduleList.add(modules);

        // create the injector
        Injector injector = Guice.createInjector(Stage.PRODUCTION, moduleList.build());

        // Create the life-cycle manager
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        // Log effective configuration
        logConfiguration(configurationFactory, unusedProperties);

        // Log managed objects
        logJMX(injector);

        // Start services
        if (lifeCycleManager.size() > 0) {
            lifeCycleManager.start();
        }

        return injector;
    }

    private static final String COMPONENT_COLUMN = "COMPONENT";
    private static final String ATTRIBUTE_NAME_COLUMN = "ATTRIBUTE";
    private static final String PROPERTY_NAME_COLUMN = "PROPERTY";
    private static final String DEFAULT_VALUE_COLUMN = "DEFAULT";
    private static final String CURRENT_VALUE_COLUMN = "RUNTIME";
    private static final String DESCRIPTION_COLUMN = "DESCRIPTION";

    private static final String CLASS_NAME_COLUMN = "NAME";
    private static final String OBJECT_NAME_COLUMN = "METHOD/ATTRIBUTE";
    private static final String TYPE_COLUMN = "TYPE";

    private void logConfiguration(ConfigurationFactory configurationFactory, Map<String, String> unusedProperties)
    {
        ColumnPrinter columnPrinter = makePrinterForConfiguration(configurationFactory);

        PrintWriter out = new PrintWriter(new LoggingWriter(log, Type.INFO));
        columnPrinter.print(out);
        out.flush();

        // Warn about unused properties
        if (!unusedProperties.isEmpty()) {
            log.warn("UNUSED PROPERTIES");
            for (Entry<String,String> unusedProperty : unusedProperties.entrySet()) {
                log.warn("%s=%s", unusedProperty.getKey(), unusedProperty.getValue());
            }
            log.warn("");
        }
    }

    private void logJMX(Injector injector)
            throws Exception
    {
        ColumnPrinter columnPrinter = makePrinterForJMX(injector);

        PrintWriter out = new PrintWriter(new LoggingWriter(log, Type.INFO));
        columnPrinter.print(out);
        out.flush();
    }

    private ColumnPrinter makePrinterForJMX(Injector injector)
            throws Exception
    {
        JmxInspector inspector = new JmxInspector(injector);

        ColumnPrinter columnPrinter = new ColumnPrinter();
        columnPrinter.addColumn(CLASS_NAME_COLUMN);
        columnPrinter.addColumn(OBJECT_NAME_COLUMN);
        columnPrinter.addColumn(TYPE_COLUMN);
        columnPrinter.addColumn(DESCRIPTION_COLUMN);

        for (JmxInspector.InspectorRecord record : inspector) {
            columnPrinter.addValue(CLASS_NAME_COLUMN, record.className);
            columnPrinter.addValue(OBJECT_NAME_COLUMN, record.objectName);
            columnPrinter.addValue(TYPE_COLUMN, record.type.name().toLowerCase());
            columnPrinter.addValue(DESCRIPTION_COLUMN, record.description);
        }
        return columnPrinter;
    }

    private ColumnPrinter makePrinterForConfiguration(ConfigurationFactory configurationFactory)
    {
        ConfigurationInspector configurationInspector = new ConfigurationInspector();

        ColumnPrinter columnPrinter = new ColumnPrinter();

        columnPrinter.addColumn(COMPONENT_COLUMN);
        columnPrinter.addColumn(ATTRIBUTE_NAME_COLUMN);
        columnPrinter.addColumn(PROPERTY_NAME_COLUMN);
        columnPrinter.addColumn(DEFAULT_VALUE_COLUMN);
        columnPrinter.addColumn(CURRENT_VALUE_COLUMN);
        columnPrinter.addColumn(DESCRIPTION_COLUMN);

        for (ConfigRecord<?> record : configurationInspector.inspect(configurationFactory)) {
            String componentName = getComponentName(record);
            for (ConfigAttribute attribute : record.getAttributes()) {
                columnPrinter.addValue(COMPONENT_COLUMN, componentName);
                columnPrinter.addValue(ATTRIBUTE_NAME_COLUMN, attribute.getAttributeName());
                columnPrinter.addValue(PROPERTY_NAME_COLUMN, attribute.getPropertyName());
                columnPrinter.addValue(DEFAULT_VALUE_COLUMN, attribute.getDefaultValue());
                columnPrinter.addValue(CURRENT_VALUE_COLUMN, attribute.getCurrentValue());
                columnPrinter.addValue(DESCRIPTION_COLUMN, attribute.getDescription());
            }
        }
        return columnPrinter;
    }

    private String getComponentName(ConfigRecord<?> record)
    {
        Key<?> key = record.getKey();
        String componentName = "";
        if (key.getAnnotationType() != null) {
            componentName = "@" + key.getAnnotationType().getSimpleName() + " ";
        }
        componentName += key.getTypeLiteral();
        return componentName;
    }
}
