package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.Module;

public class ConfigurationModule
        implements Module
{
    private final ConfigurationFactory configurationFactory;

    public ConfigurationModule(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = configurationFactory;
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(ConfigurationFactory.class).toInstance(configurationFactory);
    }

    public static <T> void bindConfig(Binder binder, Class<T> clazz)
    {
        bindConfig(binder, clazz, null);
    }

    public static <T> void bindConfig(Binder binder, Class<T> clazz, final String prefix)
    {
        StackTraceElement source = getCaller();

        if (source != null) {
            binder = binder.withSource(source);
        }
        binder.bind(clazz).toProvider(new ConfigurationProvider<T>(clazz, prefix));
    }

    private static StackTraceElement getCaller()
    {
        // find the caller of this class to report source
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean foundThisClass = false;
        for (StackTraceElement element : stack) {
            if (!foundThisClass) {
                if (element.getClassName().equals(ConfigurationModule.class.getName())){
                    foundThisClass = true;
                }
            }
            else {
                if (!element.getClassName().equals(ConfigurationModule.class.getName())){
                    return element;
                }

            }
        }
        return null;
    }
}
