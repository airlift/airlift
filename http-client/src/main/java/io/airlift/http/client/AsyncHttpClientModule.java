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
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;

import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigurationModule.bindConfig;
import static io.airlift.http.client.CompositeQualifierImpl.compositeQualifier;
import static java.util.concurrent.Executors.newFixedThreadPool;

@Beta
public class AsyncHttpClientModule
        extends AbstractHttpClientModule
{
    protected AsyncHttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        super(name, annotation);
    }

    @Override
    public Annotation getFilterQualifier()
    {
        return filterQualifier(annotation);
    }

    @Override
    public void configure()
    {
        // bind the configuration
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(HttpClientConfig.class);
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(AsyncHttpClientConfig.class);

        // bind the client and executor
        binder.bind(AsyncHttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(annotation)).in(Scopes.SINGLETON);
        binder.bind(ExecutorService.class).annotatedWith(annotation).toProvider(new ExecutorServiceProvider(name, annotation)).in(Scopes.SINGLETON);

        // kick off the binding for the filter set
        newSetBinder(binder, HttpRequestFilter.class, filterQualifier(annotation));
    }

    @Override
    public void addAlias(Class<? extends Annotation> alias)
    {
        binder.bind(AsyncHttpClient.class).annotatedWith(alias).to(Key.get(AsyncHttpClient.class, annotation));
    }

    private static class ExecutorServiceProvider implements Provider<ExecutorService>
    {
        private final String name;
        private final Class<? extends Annotation> annotation;
        private ExecutorService executorService;
        private Injector injector;

        private ExecutorServiceProvider(String name, Class<? extends Annotation> annotation)
        {
            this.name = name;
            this.annotation = annotation;
        }

        @PreDestroy
        public void stop()
        {
            if (executorService != null) {
                executorService.shutdownNow();
                executorService = null;
            }
        }

        @Inject
        public void setInjector(Injector injector)
        {

            this.injector = injector;
        }

        @Override
        public ExecutorService get()
        {
            if (executorService == null) {
                AsyncHttpClientConfig asyncConfig = injector.getInstance(Key.get(AsyncHttpClientConfig.class, annotation));
                executorService = newFixedThreadPool(asyncConfig.getWorkerThreads(), new ThreadFactoryBuilder().setDaemon(true).setNameFormat(name + "-http-client-%s").build());
            }
            return executorService;
        }
    }

    private static class HttpClientProvider implements Provider<AsyncHttpClient>
    {
        private final Class<? extends Annotation> annotation;
        private Injector injector;

        private HttpClientProvider(Class<? extends Annotation> annotation)
        {
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
            ExecutorService executorService = injector.getInstance(Key.get(ExecutorService.class, annotation));
            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));
            Set<HttpRequestFilter> filters = injector.getInstance(filterKey(annotation));
            return new AsyncHttpClient(new ApacheHttpClient(config), executorService, filters);
        }
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
