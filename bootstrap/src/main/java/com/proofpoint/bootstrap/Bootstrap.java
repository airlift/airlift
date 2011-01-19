package com.proofpoint.bootstrap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationInspector;
import com.proofpoint.configuration.ConfigurationLoader;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.configuration.ConfigurationValidator;
import com.proofpoint.configuration.ValidationErrorModule;
import com.proofpoint.jmx.JMXInspector;
import com.proofpoint.lifecycle.LifeCycleManager;
import com.proofpoint.lifecycle.LifeCycleModule;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;
import com.proofpoint.log.LoggingWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Entry point for an application built using the platform codebase.
 *
 * This class will:
 * <ul>
 *  <li>load, validate and bind configurations</li>
 *  <li>initialize logging</li>
 *  <li>set up lifecycle management</li>
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
        ConfigurationInspector configurationInspector = injector.getInstance(ConfigurationInspector.class);
        configurationInspector.print(new PrintWriter(new LoggingWriter(log, LoggingWriter.Type.DEBUG)));

        // Log managed objects
        JMXInspector jmxInspector = injector.getInstance(JMXInspector.class);
        jmxInspector.print(new PrintWriter(new LoggingWriter(log, LoggingWriter.Type.DEBUG)));

        // Start services
        if (lifeCycleManager.size() > 0) {
            lifeCycleManager.start();
        }

        return injector;
    }

}
