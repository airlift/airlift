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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.client.jetty.JettyIoPool;
import io.airlift.http.client.jetty.JettyIoPoolConfig;
import io.airlift.log.Logger;

import javax.annotation.PreDestroy;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigurationModule.bindConfig;
import static io.airlift.http.client.CompositeQualifierImpl.compositeQualifier;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

@Beta
public class AsyncHttpClientModule
        extends AbstractHttpClientModule
{
    private static final Logger log = Logger.get(AsyncHttpClientModule.class);

    protected AsyncHttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        super(name, annotation);
    }

    @Override
    public Annotation getFilterQualifier()
    {
        return filterQualifier(annotation);
    }

    void withPrivateIoThreadPool()
    {
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(JettyIoPoolConfig.class);
        binder.bind(JettyIoPoolManager.class).annotatedWith(annotation).toInstance(new JettyIoPoolManager(name, annotation));
    }

    @Override
    public void configure()
    {
        // bind the configuration
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(HttpClientConfig.class);

        // Shared thread pool
        bindConfig(binder).to(JettyIoPoolConfig.class);
        binder.bind(JettyIoPoolManager.class).to(SharedJettyIoPoolManager.class).in(Scopes.SINGLETON);

        // bind the async client
        binder.bind(AsyncHttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(name, annotation)).in(Scopes.SINGLETON);

        // bind the a sync client also
        binder.bind(HttpClient.class).annotatedWith(annotation).to(Key.get(AsyncHttpClient.class, annotation));

        // kick off the binding for the filter set
        newSetBinder(binder, HttpRequestFilter.class, filterQualifier(annotation));

        // export stats
        newExporter(binder).export(AsyncHttpClient.class).annotatedWith(annotation).withGeneratedName();
    }

    @Override
    public void addAlias(Class<? extends Annotation> alias)
    {
        binder.bind(AsyncHttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotation));
        binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotation));
    }

    private static class HttpClientProvider
            implements Provider<AsyncHttpClient>
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
        public AsyncHttpClient get()
        {
            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));
            Set<HttpRequestFilter> filters = injector.getInstance(filterKey(annotation));

            JettyIoPoolManager ioPoolProvider;
            if (injector.getExistingBinding(Key.get(JettyIoPoolManager.class, annotation)) != null) {
                log.debug("HttpClient %s uses private IO thread pool", name);
                ioPoolProvider = injector.getInstance(Key.get(JettyIoPoolManager.class, annotation));
            }
            else {
                log.debug("HttpClient %s uses shared IO thread pool", name);
                ioPoolProvider = injector.getInstance(JettyIoPoolManager.class);
            }

            JettyHttpClient client = new JettyHttpClient(config, ioPoolProvider.get(), ImmutableList.copyOf(filters));
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

    private static Key<Set<HttpRequestFilter>> filterKey(Class<? extends Annotation> annotation)
    {
        return Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, filterQualifier(annotation));
    }

    private static CompositeQualifier filterQualifier(Class<? extends Annotation> annotation)
    {
        return compositeQualifier(annotation, AsyncHttpClient.class);
    }
}
