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
package io.airlift.configuration;

import com.google.inject.Binder;
import com.google.inject.Module;

import java.lang.annotation.Annotation;

import static io.airlift.configuration.ConfigBinder.configBinder;

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
        binder.disableCircularProxies();

        binder.bind(ConfigurationFactory.class).toInstance(configurationFactory);

        configurationFactory.getConfigurationProviders().forEach(configurationProvider -> bindConfigurationProvider(binder, configurationProvider));
    }

    private static <T> void bindConfigurationProvider(Binder binder, ConfigurationProvider<T> configurationProvider)
    {
        binder.bind(configurationProvider.getConfigurationBinding().getKey()).toProvider(configurationProvider);
    }

    /**
     * @deprecated As of Airlift 0.109, replaced by {@link ConfigBinder#configBinder(Binder)}.
     */
    @Deprecated
    public static AnnotatedBindingBuilder bindConfig(Binder binder)
    {
        return new AnnotatedBindingBuilder(binder.skipSources(ConfigurationModule.class));
    }

    public static class AnnotatedBindingBuilder
            extends PrefixBindingBuilder
    {
        public AnnotatedBindingBuilder(Binder binder)
        {
            super(binder, null, null);
        }

        public PrefixBindingBuilder annotatedWith(Class<? extends Annotation> annotationType)
        {
            return new PrefixBindingBuilder(binder, annotationType, null);
        }

        public PrefixBindingBuilder annotatedWith(Annotation annotation)
        {
            return new PrefixBindingBuilder(binder, null, annotation);
        }
    }

    public static class PrefixBindingBuilder
            extends ConfigBindingBuilder
    {
        public PrefixBindingBuilder(Binder binder, Class<? extends Annotation> annotationType, Annotation annotation)
        {
            super(binder, annotationType, annotation, null);
        }

        public ConfigBindingBuilder prefixedWith(String prefix)
        {
            return new ConfigBindingBuilder(binder, annotationType, annotation, prefix);
        }
    }

    public static class ConfigBindingBuilder
    {
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

        public <T> void to(Class<T> configClass)
        {
            if (annotationType != null) {
                configBinder(binder).bindConfig(configClass, annotationType, prefix);
            }
            else if (annotation != null) {
                configBinder(binder).bindConfig(configClass, annotation, prefix);
            }
            else {
                configBinder(binder).bindConfig(configClass, prefix);
            }
        }
    }
}
