package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.Module;

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

    public static <T> void bindConfig(Binder binder, Class<T> configClass)
    {
        bindConfig(binder).to(configClass);
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
            return new PrefixBindingBuilder(binder, annotationType, null);
        }

        public PrefixBindingBuilder annotatedWith(Annotation annotation) {
            return new PrefixBindingBuilder(binder, null, annotation);
        }
    }

    public static class PrefixBindingBuilder extends ConfigBindingBuilder {
        public PrefixBindingBuilder(Binder binder, Class<? extends Annotation> annotationType, Annotation annotation)
        {
            super(binder, annotationType, annotation, null);
        }

        public ConfigBindingBuilder prefixedWith(String prefix) {
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
            if (annotationType != null) {
                binder.bind(configClass).annotatedWith(annotationType).toProvider(new ConfigurationProvider<T>(configClass, prefix));
            } else if(annotation != null) {
                binder.bind(configClass).annotatedWith(annotation).toProvider(new ConfigurationProvider<T>(configClass, prefix));
            } else {
                binder.bind(configClass).toProvider(new ConfigurationProvider<T>(configClass, prefix));
            }

        }
    }
}
