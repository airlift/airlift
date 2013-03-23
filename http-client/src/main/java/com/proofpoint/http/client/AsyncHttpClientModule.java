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
package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.proofpoint.http.client.netty.NettyAsyncHttpClient;
import com.proofpoint.http.client.netty.NettyIoPool;
import com.proofpoint.http.client.netty.NettyIoPoolConfig;
import com.proofpoint.log.Logger;
import com.proofpoint.http.client.netty.NettyAsyncHttpClientConfig;

import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.http.client.CompositeQualifierImpl.compositeQualifier;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

@Beta
public class AsyncHttpClientModule
        extends AbstractHttpClientModule
{
    private static final Logger log = Logger.get(AsyncHttpClientModule.class);

    protected AsyncHttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        super(name, annotation, null);
    }

    protected AsyncHttpClientModule(String name, Class<? extends Annotation> annotation, Binder rootBinder)
    {
        super(name, annotation, checkNotNull(rootBinder, "rootBinder is null"));
    }

    @Override
    public Annotation getFilterQualifier()
    {
        return filterQualifier(annotation);
    }

    void withPrivateIoThreadPool()
    {
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(NettyIoPoolConfig.class);
        binder.bind(NettyIoPool.class).annotatedWith(annotation).toProvider(new NettyIoPoolProvider(name, annotation)).in(Scopes.SINGLETON);
    }

    @Override
    public void configure()
    {
        // bind the configuration
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(HttpClientConfig.class);
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(NettyAsyncHttpClientConfig.class);

        // Shared thread pool
        bindConfig(binder).to(NettyIoPoolConfig.class);
        binder.bind(NettyIoPool.class).toProvider(SharedNettyIoPoolProvider.class).in(Scopes.SINGLETON);

        // bind the async client
        binder.bind(AsyncHttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(name, annotation)).in(Scopes.SINGLETON);

        // bind the a sync client also
        binder.bind(HttpClient.class).annotatedWith(annotation).to(Key.get(AsyncHttpClient.class, annotation));

        // kick off the binding for the filter set
        newSetBinder(binder, HttpRequestFilter.class, filterQualifier(annotation));

        // export stats
        newExporter(firstNonNull(rootBinder, binder)).export(AsyncHttpClient.class).annotatedWith(annotation).withGeneratedName();
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
        private final List<NettyAsyncHttpClient> clients = new ArrayList<>();
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

        @PreDestroy
        public void destroy()
        {
            for (NettyAsyncHttpClient client : clients) {
                client.close();
            }
        }

        @Override
        public AsyncHttpClient get()
        {
            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));
            NettyAsyncHttpClientConfig asyncConfig = injector.getInstance(Key.get(NettyAsyncHttpClientConfig.class, annotation));
            Set<HttpRequestFilter> filters = injector.getInstance(filterKey(annotation));

            NettyIoPool ioPool;
            if (injector.getExistingBinding(Key.get(NettyIoPool.class, annotation)) != null) {
                ioPool = injector.getInstance(Key.get(NettyIoPool.class, annotation));
                log.debug("HttpClient %s uses private IO thread pool", name);
            }
            else {
                log.debug("HttpClient %s uses shared IO thread pool", name);
                ioPool = injector.getInstance(NettyIoPool.class);
            }

            NettyAsyncHttpClient client = new NettyAsyncHttpClient(name, ioPool, config, asyncConfig, filters);
            clients.add(client);
            return client;
        }
    }

    private static class SharedNettyIoPoolProvider
            extends NettyIoPoolProvider
    {
        private SharedNettyIoPoolProvider()
        {
            super("shared", null);
        }
    }

    private static class NettyIoPoolProvider
            implements Provider<NettyIoPool>
    {
        private final List<NettyIoPool> pools = new ArrayList<>();
        private final String name;
        private final Class<? extends Annotation> annotation;
        private Injector injector;

        private NettyIoPoolProvider(String name, Class<? extends Annotation> annotation)
        {
            this.name = name;
            this.annotation = annotation;
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @PreDestroy
        public void destroy()
        {
            for (NettyIoPool pool : pools) {
                pool.close();
            }
        }

        @Override
        public NettyIoPool get()
        {
            NettyIoPoolConfig config = injector.getInstance(keyFromNullable(NettyIoPoolConfig.class, annotation));
            NettyIoPool pool = new NettyIoPool(name, config);
            pools.add(pool);
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
