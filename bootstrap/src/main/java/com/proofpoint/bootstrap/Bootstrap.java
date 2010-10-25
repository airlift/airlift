package com.proofpoint.bootstrap;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationLoader;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.guice.BootstrapElements;
import com.proofpoint.lifecycle.LifeCycleManager;
import com.proofpoint.lifecycle.LifeCycleModule;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;

import java.io.IOException;
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

        log.info("Loading configuration");
        ConfigurationLoader loader = new ConfigurationLoader();
        Map<String, String> properties = loader.loadProperties();

        // set up logging
        log.info("Initializing logging");
        final ConfigurationFactory          factory = new ConfigurationFactory(properties);
        LoggingConfiguration configuration = factory.build(LoggingConfiguration.class);
        logging.initialize(configuration);

        BootstrapElements       bootstrapElements = new BootstrapElements(modules);

        LifeCycleModule         lifeCycleModule = new LifeCycleModule();

        // load & configure guice modules
        ConfigurationModule     config = new ConfigurationModule(properties, bootstrapElements);

        Injector                injector = Guice.createInjector
        (
            Stage.PRODUCTION,
            lifeCycleModule,
            config,
            bootstrapElements,  // must come after config
            new Module()
            {
                @Override
                public void configure(Binder binder)
                {
                    binder.bind(ConfigurationFactory.class).toInstance(factory);
                }
            }
        );
        LifeCycleManager        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        if ( lifeCycleManager.size() > 0 )
        {
            lifeCycleManager.start();
        }

        return injector;
    }
}
