package com.proofpoint.bootstrap;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationLoader;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.lifecycle.LifeCycleModule;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Bootstrap
{
    private final Logger log =  Logger.get(Bootstrap.class);
    private final Module[] modules;

    public Bootstrap(Module... modules)
            throws IOException
    {
        this.modules = modules;
    }

    public Injector initialize()
            throws IOException
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

        log.info("Loading configuration");
        ConfigurationLoader loader = new ConfigurationLoader();
        Map<String, String> properties = loader.loadProperties();

        // set up logging
        log.info("Initializing logging");
        final ConfigurationFactory          factory = new ConfigurationFactory(properties);
        LoggingConfiguration configuration = factory.build(LoggingConfiguration.class);
        logging.initialize(configuration);

        List<Element>           elementList = Elements.getElements(modules);

        final LifeCycleModule   lifeCycleModule = new LifeCycleModule(modules);

        // load & configure guice modules
        ConfigurationModule config = new ConfigurationModule(properties, elementList)
        {
            @Override
            public void configure(Binder binder)
            {
                binder.install(lifeCycleModule);
                binder.bind(ConfigurationFactory.class).toInstance(factory);

                super.configure(binder);
            }
        };

        return Guice.createInjector(config);
    }
}
