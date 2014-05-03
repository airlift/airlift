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
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
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

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigurationModule.bindConfig;
import static io.airlift.http.client.CompositeQualifierImpl.compositeQualifier;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

@Beta
public class AsyncHttpClientModule
        implements Module
{
    private static final Logger log = Logger.get(AsyncHttpClientModule.class);
    protected final String name;
    protected final Class<? extends Annotation> annotationType;
    protected final Annotation annotation;
    protected Binder binder;

    protected AsyncHttpClientModule(String name, Class<? extends Annotation> annotationType)
    {
        this.name = checkNotNull(name, "name is null");
        this.annotationType = checkNotNull(annotationType, "annotationType is null");
        annotation = null;
    }

    public AsyncHttpClientModule(String name, Annotation annotation)
    {
        this.name = checkNotNull(name, "name is null");
        annotationType = null;
        this.annotation = checkNotNull(annotation, "annotation is null");
    }

    public Annotation getFilterQualifier()
    {
        return firstNonNull(annotation, filterQualifier(annotationType));
    }

    void withPrivateIoThreadPool()
    {
        bindConfig(binder).annotatedWith(getFilterQualifier()).prefixedWith(name).to(JettyIoPoolConfig.class);
        binder.bind(JettyIoPoolManager.class).annotatedWith(getFilterQualifier()).toInstance(new JettyIoPoolManager(name, getFilterQualifier()));
    }

    @Override
    public final void configure(Binder binder)
    {
        this.binder = binder;
        configure();
    }

    public void configure()
    {
        // bind the configuration
        bindConfig(binder).annotatedWith(getFilterQualifier()).prefixedWith(name).to(HttpClientConfig.class);

        // Shared thread pool
        bindConfig(binder).to(JettyIoPoolConfig.class);
        binder.bind(JettyIoPoolManager.class).to(SharedJettyIoPoolManager.class).in(Scopes.SINGLETON);

        if (annotationType != null) {
            // bind the async client
            binder.bind(AsyncHttpClient.class).annotatedWith(annotationType).toProvider(new HttpClientProvider(name, getFilterQualifier())).in(Scopes.SINGLETON);

            // bind the a sync client also
            binder.bind(HttpClient.class).annotatedWith(annotationType).to(Key.get(AsyncHttpClient.class, annotationType));

            // export stats
            newExporter(binder).export(AsyncHttpClient.class).annotatedWith(annotationType).withGeneratedName();
        }
        else {
            // bind the async client
            binder.bind(AsyncHttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(name, annotation)).in(Scopes.SINGLETON);

            // bind the a sync client also
            binder.bind(HttpClient.class).annotatedWith(annotation).to(Key.get(AsyncHttpClient.class, annotation));

            // export stats
            newExporter(binder).export(AsyncHttpClient.class).annotatedWith(annotation).withGeneratedName();
        }

        // kick off the binding for the filter set
        newSetBinder(binder, HttpRequestFilter.class, getFilterQualifier());
    }

    public void addAlias(Class<? extends Annotation> alias)
    {
        if (annotationType != null) {
            binder.bind(AsyncHttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotationType));
            binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotationType));
        }
        else {
            binder.bind(AsyncHttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotation));
            binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotation));
        }
    }

    public void addAlias(Annotation alias)
    {
        if (annotationType != null) {
            binder.bind(AsyncHttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotationType));
            binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotationType));
        }
        else {
            binder.bind(AsyncHttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotation));
            binder.bind(HttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotation));
        }
    }

    private static class HttpClientProvider
            implements Provider<AsyncHttpClient>
    {
        private final String name;
        private final Annotation annotation;
        private Injector injector;

        private HttpClientProvider(String name, Annotation annotation)
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
        private final Annotation annotation;
        private final AtomicBoolean destroyed = new AtomicBoolean();
        private JettyIoPool pool;
        private Injector injector;

        private JettyIoPoolManager(String name, Annotation annotation)
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

    private static <T> Key<T> keyFromNullable(Class<T> type, Annotation annotation)
    {
        return (annotation != null) ? Key.get(type, annotation) : Key.get(type);
    }

    private static Key<Set<HttpRequestFilter>> filterKey(Annotation annotation)
    {
        return Key.get(new TypeLiteral<Set<HttpRequestFilter>>() {}, annotation);
    }

    private static CompositeQualifier filterQualifier(Class<? extends Annotation> annotation)
    {
        return compositeQualifier(annotation, AsyncHttpClient.class);
    }
}
