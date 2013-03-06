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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import io.airlift.http.client.netty.NettyAsyncHttpClient;

import java.lang.annotation.Annotation;
import java.util.Set;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigurationModule.bindConfig;
import static io.airlift.http.client.CompositeQualifierImpl.compositeQualifier;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

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

        // bind the async client
        binder.bind(AsyncHttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(annotation)).in(Scopes.SINGLETON);

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
            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));
            AsyncHttpClientConfig asyncConfig = injector.getInstance(Key.get(AsyncHttpClientConfig.class, annotation));
            Set<HttpRequestFilter> filters = injector.getInstance(filterKey(annotation));
            return new NettyAsyncHttpClient(config, asyncConfig, filters);
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
