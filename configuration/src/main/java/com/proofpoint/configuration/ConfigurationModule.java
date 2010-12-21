package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.Module;
import static com.proofpoint.configuration.ConfigurationProvider.createLegacyConfigurationProvider;

import java.lang.annotation.Annotation;

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

    /**
     * @deprecated Keep using this for now, but be aware that a new configuration format is in the works.
     */
    @Deprecated
    public static <T> void bindConfig(Binder binder, Class<T> configClass)
    {
        ConfigurationProvider<T> configurationProvider = createLegacyConfigurationProvider(configClass);
        binder.bind(configClass).toProvider(configurationProvider);
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

    public static AnnotatedBindingBuilder bindConfig(Binder binder) {
        return new AnnotatedBindingBuilder(binder.withSource(getCaller()));
    }

    public static class AnnotatedBindingBuilder extends PrefixBindingBuilder {
        public AnnotatedBindingBuilder(Binder binder)
        {
            super(binder, null, null);
        }

        public PrefixBindingBuilder annotatedWith(Class<? extends Annotation> annotationType) {
            if (annotationType == null) {
                throw new NullPointerException("annotationType is null");
            }

            return new PrefixBindingBuilder(binder, annotationType, null);
        }

        public PrefixBindingBuilder annotatedWith(Annotation annotation) {
            if (annotation == null) {
                throw new NullPointerException("annotation is null");
            }

            return new PrefixBindingBuilder(binder, null, annotation);
        }
    }

    public static class PrefixBindingBuilder extends ConfigBindingBuilder {
        public PrefixBindingBuilder(Binder binder, Class<? extends Annotation> annotationType, Annotation annotation)
        {
            super(binder, annotationType, annotation, null);
        }

        public ConfigBindingBuilder prefixedWith(String prefix) {
            if (prefix == null) {
                throw new NullPointerException("prefix is null");
            }

            return new ConfigBindingBuilder(binder, annotationType, annotation,  prefix);
        }
    }

    public static class ConfigBindingBuilder {
        protected final Binder binder;
        protected final Class<? extends Annotation> annotationType;
        protected final Annotation annotation;
        protected final String prefix;

        public ConfigBindingBuilder(Binder binder, Class<? extends Annotation> annotationType, Annotation annotation, String prefix)
        {
            this.binder = binder;
            this.annotationType = annotationType;
            this.annotation = annotation;
            this.prefix = prefix;
        }

        public <T> void to(Class<T> configClass) {
            if (configClass == null) {
                throw new NullPointerException("configClass is null");
            }

            ConfigurationProvider<T> configurationProvider = new ConfigurationProvider<T>(configClass, prefix);
            if (annotationType != null) {
                binder.bind(configClass).annotatedWith(annotationType).toProvider(configurationProvider);
            } else if(annotation != null) {
                binder.bind(configClass).annotatedWith(annotation).toProvider(configurationProvider);
            } else {
                binder.bind(configClass).toProvider(configurationProvider);
            }
        }

        public <T> void to(T defaultInstance) {
            if (defaultInstance == null) {
                throw new NullPointerException("defaultInstance is null");
            }

            ConfigurationProvider<T> configurationProvider = new ConfigurationProvider<T>(defaultInstance, prefix);
            if (annotationType != null) {
                binder.bind(configurationProvider.getConfigClass()).annotatedWith(annotationType).toProvider(configurationProvider);
            } else if(annotation != null) {
                binder.bind(configurationProvider.getConfigClass()).annotatedWith(annotation).toProvider(configurationProvider);
            } else {
                binder.bind(configurationProvider.getConfigClass()).toProvider(configurationProvider);
            }
        }
    }
}
