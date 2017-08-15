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
import com.google.inject.Scopes;
import io.airlift.discovery.client.ServiceSelector;
import org.weakref.jmx.guice.MBeanModule;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

import java.lang.annotation.Annotation;
import java.util.List;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static io.airlift.discovery.client.ServiceTypes.serviceType;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class PostgreSqlDataSourceModule
        implements Module
{
    private final String type;
    private final Class<? extends Annotation> annotation;
    private final List<Class<? extends Annotation>> aliases;

    @SafeVarargs
    public PostgreSqlDataSourceModule(String type, Class<? extends Annotation> annotation, Class<? extends Annotation>... aliases)
    {
        this.type = requireNonNull(type, "type is null");
        this.annotation = requireNonNull(annotation, "annotation is null");

        if (aliases != null) {
            this.aliases = ImmutableList.copyOf(aliases);
        }
        else {
            this.aliases = ImmutableList.of();
        }
    }

    @Override
    public void configure(Binder binder)
    {
        binder.install(new MBeanModule());

        // bind the configuration
        configBinder(binder).bindConfig(PostgreSqlDataSourceConfig.class, annotation, type);

        // bind the service selector
        discoveryBinder(binder).bindSelector(type);

        // Bind the datasource
        binder.bind(DataSource.class).annotatedWith(annotation).toProvider(new PostgreSqlDataSourceProvider(type, annotation)).in(Scopes.SINGLETON);
        newExporter(binder).export(DataSource.class).annotatedWith(annotation).withGeneratedName();

        // Bind aliases
        Key<DataSource> key = Key.get(DataSource.class, annotation);
        for (Class<? extends Annotation> alias : aliases) {
            binder.bind(DataSource.class).annotatedWith(alias).to(key);
        }
    }

    private static class PostgreSqlDataSourceProvider
            implements Provider<PostgreSqlDataSource>
    {
        private final String type;
        private final Class<? extends Annotation> annotation;
        private Injector injector;

        private PostgreSqlDataSourceProvider(String type, Class<? extends Annotation> annotation)
        {
            this.type = type;
            this.annotation = annotation;
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @Override
        public PostgreSqlDataSource get()
        {
            PostgreSqlDataSourceConfig config = injector.getInstance(Key.get(PostgreSqlDataSourceConfig.class, annotation));
            ServiceSelector serviceSelector = injector.getInstance(Key.get(ServiceSelector.class, serviceType(type)));
            return new PostgreSqlDataSource(serviceSelector, config);
        }
    }
}
