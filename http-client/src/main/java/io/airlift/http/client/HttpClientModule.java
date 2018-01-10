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
package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import io.airlift.configuration.ConfigDefaults;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.client.spnego.KerberosConfig;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

import java.lang.annotation.Annotation;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class HttpClientModule
        implements Module
{
    protected final String name;
    protected final Class<? extends Annotation> annotation;
    protected Binder binder;

    HttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        this.name = requireNonNull(name, "name is null");
        this.annotation = requireNonNull(annotation, "annotation is null");
    }

    void withConfigDefaults(ConfigDefaults<HttpClientConfig> configDefaults)
    {
        configBinder(binder).bindConfigDefaults(HttpClientConfig.class, annotation, configDefaults);
    }

    @Override
    public void configure(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null");

        // bind the configuration
        configBinder(binder).bindConfig(KerberosConfig.class);
        configBinder(binder).bindConfig(HttpClientConfig.class, annotation, name);

        // bind the client
        binder.bind(HttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(name, annotation)).in(Scopes.SINGLETON);

        // kick off the binding for the default filters
        newSetBinder(binder, HttpRequestFilter.class, GlobalFilter.class);

        // kick off the binding for the filter set
        newSetBinder(binder, HttpRequestFilter.class, annotation);

        // export stats
        newExporter(binder).export(HttpClient.class).annotatedWith(annotation).withGeneratedName();
    }

    public void addAlias(Class<? extends Annotation> alias)
    {
        binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(HttpClient.class, annotation));
    }

    private static class HttpClientProvider
            implements Provider<HttpClient>
    {
        private final String name;
        private final Class<? extends Annotation> annotation;
        private Injector injector;
        private HttpClient client;

        private HttpClientProvider(String name, Class<? extends Annotation> annotation)
        {
            this.name = requireNonNull(name, "name is null");
            this.annotation = requireNonNull(annotation, "annotation is null");
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @Override
        public HttpClient get()
        {
            checkState(client == null, "client already created");

            KerberosConfig kerberosConfig = injector.getInstance(KerberosConfig.class);
            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));

            Set<HttpRequestFilter> filters = ImmutableSet.<HttpRequestFilter>builder()
                    .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, GlobalFilter.class)))
                    .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, annotation)))
                    .build();

            client = new JettyHttpClient(name, config, kerberosConfig, ImmutableList.copyOf(filters));

            injector = null;
            return client;
        }

        @PreDestroy
        public void destroy()
        {
            client.close();
        }
    }
}
