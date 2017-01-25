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

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
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
import io.airlift.http.client.jetty.JettyIoPool;
import io.airlift.http.client.jetty.JettyIoPoolConfig;
import io.airlift.http.client.spnego.KerberosConfig;
import io.airlift.log.Logger;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

@Beta
public class HttpClientModule
        implements Module
{
    private static final Logger log = Logger.get(HttpClientModule.class);
    protected final String name;
    protected final Class<? extends Annotation> annotation;
    protected Binder binder;

    public HttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        this.name = requireNonNull(name, "name is null");
        this.annotation = requireNonNull(annotation, "annotation is null");
    }

    void withConfigDefaults(ConfigDefaults<HttpClientConfig> configDefaults)
    {
        configBinder(binder).bindConfigDefaults(HttpClientConfig.class, annotation, configDefaults);
    }

    void withPrivateIoThreadPool()
    {
        configBinder(binder).bindConfig(JettyIoPoolConfig.class, annotation, name);
        binder.bind(JettyIoPoolManager.class).annotatedWith(annotation).toInstance(new JettyIoPoolManager(name, annotation));
    }

    @Override
    public void configure(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null");

        // bind the configuration
        configBinder(binder).bindConfig(KerberosConfig.class);
        configBinder(binder).bindConfig(HttpClientConfig.class, annotation, name);

        // Shared thread pool
        configBinder(binder).bindConfig(JettyIoPoolConfig.class);
        binder.bind(JettyIoPoolManager.class).to(SharedJettyIoPoolManager.class).in(Scopes.SINGLETON);

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

        private HttpClientProvider(String name, Class<? extends Annotation> annotation)
        {
            this.name = name;
            this.annotation = annotation;
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @Override
        public HttpClient get()
        {
            KerberosConfig kerberosConfig = injector.getInstance(KerberosConfig.class);

            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));
            Set<HttpRequestFilter> filters = ImmutableSet.<HttpRequestFilter>builder()
                    .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, GlobalFilter.class)))
                    .addAll(injector.getInstance(Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, annotation)))
                    .build();

            JettyIoPoolManager ioPoolProvider;
            if (injector.getExistingBinding(Key.get(JettyIoPoolManager.class, annotation)) != null) {
                log.debug("HttpClient %s uses private IO thread pool", name);
                ioPoolProvider = injector.getInstance(Key.get(JettyIoPoolManager.class, annotation));
            }
            else {
                log.debug("HttpClient %s uses shared IO thread pool", name);
                ioPoolProvider = injector.getInstance(JettyIoPoolManager.class);
            }

            JettyHttpClient client = new JettyHttpClient(config, kerberosConfig, Optional.of(ioPoolProvider.get()), ImmutableList.copyOf(filters));
            ioPoolProvider.addClient(client);
            return client;
        }
    }

    private static class SharedJettyIoPoolManager
            extends JettyIoPoolManager
    {
        private SharedJettyIoPoolManager()
        {
            super("shared", null);
        }
    }

    @VisibleForTesting
    public static class JettyIoPoolManager
    {
        private final List<JettyHttpClient> clients = new ArrayList<>();
        private final String name;
        private final Class<? extends Annotation> annotation;
        private final AtomicBoolean destroyed = new AtomicBoolean();
        private JettyIoPool pool;
        private Injector injector;

        private JettyIoPoolManager(String name, Class<? extends Annotation> annotation)
        {
            this.name = name;
            this.annotation = annotation;
        }

        public void addClient(JettyHttpClient client)
        {
            clients.add(client);
        }

        public boolean isDestroyed()
        {
            return destroyed.get();
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @PreDestroy
        public void destroy()
        {
            // clients must be destroyed before the pools or
            // you will create a several second busy wait loop
            for (JettyHttpClient client : clients) {
                client.close();
            }
            if (pool != null) {
                pool.close();
                pool = null;
            }
            destroyed.set(true);
        }

        public JettyIoPool get()
        {
            if (pool == null) {
                JettyIoPoolConfig config = injector.getInstance(keyFromNullable(JettyIoPoolConfig.class, annotation));
                pool = new JettyIoPool(name, config);
            }
            return pool;
        }
    }

    private static <T> Key<T> keyFromNullable(Class<T> type, Class<? extends Annotation> annotation)
    {
        return (annotation != null) ? Key.get(type, annotation) : Key.get(type);
    }
}
