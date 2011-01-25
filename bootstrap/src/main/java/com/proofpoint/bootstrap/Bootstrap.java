package com.proofpoint.bootstrap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
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
import com.proofpoint.jmx.JmxInspector;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

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

    public Bootstrap(Module... modules)
    {
        this.modules = modules;
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
        Map<String, String> properties = loader.loadProperties();
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);

        // initialize logging
        log.info("Initializing logging");
        LoggingConfiguration configuration = configurationFactory.build(LoggingConfiguration.class);
        logging.initialize(configuration);

        // Validate configuration
        ConfigurationValidator configurationValidator = new ConfigurationValidator(configurationFactory);
        List<Message> messages = configurationValidator.validate(modules);

        // system modules
        Builder<Module> moduleList = ImmutableList.builder();
        moduleList.add(new LifeCycleModule());
        moduleList.add(new ConfigurationModule(configurationFactory));
        if (!messages.isEmpty()) {
            moduleList.add(new ValidationErrorModule(messages));
        }
        moduleList.add(modules);

        // create the injector
        Injector injector = Guice.createInjector(Stage.PRODUCTION, moduleList.build());

        // Create the life-cycle manager
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        // Log effective configuration
        logConfiguration(configurationFactory);

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

    private void logConfiguration(ConfigurationFactory configurationFactory)
    {
        ColumnPrinter columnPrinter = makePrinterForConfiguration(configurationFactory);

        PrintWriter out = new PrintWriter(new LoggingWriter(log, Type.INFO));
        columnPrinter.print(out);
        out.flush();
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
