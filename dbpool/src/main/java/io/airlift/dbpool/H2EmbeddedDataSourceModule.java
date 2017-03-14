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
package io.airlift.dbpool;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import org.weakref.jmx.guice.MBeanModule;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class H2EmbeddedDataSourceModule
        implements Module
{
    private final Class<? extends Annotation> annotation;
    private final List<Class<? extends Annotation>> aliases;
    private final String propertyPrefix;

    @SafeVarargs
    public H2EmbeddedDataSourceModule(String propertyPrefix, Class<? extends Annotation> annotation, Class<? extends Annotation>... aliases)
    {
        if (annotation == null) {
            throw new NullPointerException("annotation is null");
        }
        if (propertyPrefix == null) {
            throw new NullPointerException("propertyPrefix is null");
        }
        if (propertyPrefix.isEmpty()) {
            throw new IllegalArgumentException("propertyPrefix is empty");
        }
        this.annotation = annotation;
        this.propertyPrefix = propertyPrefix;
        if (aliases != null) {
            this.aliases = ImmutableList.copyOf(aliases);
        }
        else {
            this.aliases = Collections.emptyList();
        }
    }

    @Override
    public void configure(Binder binder)
    {
        binder.install(new MBeanModule());

        // bind the configuration
        configBinder(binder).bindConfig(H2EmbeddedDataSourceConfig.class, annotation, propertyPrefix);

        // Bind the datasource
        binder.bind(DataSource.class).annotatedWith(annotation).toProvider(new H2EmbeddedDataSourceProvider(annotation)).in(Scopes.SINGLETON);
        newExporter(binder).export(DataSource.class).annotatedWith(annotation).withGeneratedName();

        // Bind aliases
        Key<DataSource> key = Key.get(DataSource.class, annotation);
        for (Class<? extends Annotation> alias : aliases) {
            binder.bind(DataSource.class).annotatedWith(alias).to(key);
        }
    }

    private static class H2EmbeddedDataSourceProvider
            implements Provider<H2EmbeddedDataSource>
    {
        private final Class<? extends Annotation> annotation;
        private Injector injector;

        private H2EmbeddedDataSourceProvider(Class<? extends Annotation> annotation)
        {
            this.annotation = annotation;
        }

        @Inject
        public void setInjector(Injector injector)
        {

            this.injector = injector;
        }

        @Override
        public H2EmbeddedDataSource get()
        {
            H2EmbeddedDataSourceConfig config = injector.getInstance(Key.get(H2EmbeddedDataSourceConfig.class, annotation));
            try {
                return new H2EmbeddedDataSource(config);
            }
            catch (Exception e) {
                throw new ProvisionException("Error creating a H2EmbeddedDataSource", e);
            }
        }
    }
}
