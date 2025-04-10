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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import io.airlift.configuration.ConfigDefaults;
import org.eclipse.jetty.io.ByteBufferPool;

import java.lang.annotation.Annotation;
import java.util.Collection;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static java.util.Objects.requireNonNull;

public class HttpClientBinder
{
    private final Binder binder;
    private final Multibinder<HttpRequestFilter> globalFilterBinder;
    private final Multibinder<HttpStatusListener> globalStatusListenerBinder;
    private final OptionalBinder<ByteBufferPool> byteBufferPool;

    private HttpClientBinder(Binder binder)
    {
        this.binder = binder.skipSources(getClass());
        this.globalFilterBinder = newSetBinder(binder, HttpRequestFilter.class, GlobalFilter.class);
        this.globalStatusListenerBinder = newSetBinder(binder, HttpStatusListener.class, GlobalFilter.class);
        this.byteBufferPool = newOptionalBinder(binder, ByteBufferPool.class);
    }

    public static HttpClientBinder httpClientBinder(Binder binder)
    {
        return new HttpClientBinder(binder);
    }

    public HttpClientBindingBuilder bindHttpClient(String name, Class<? extends Annotation> annotation)
    {
        HttpClientModule module = new HttpClientModule(name, annotation);
        binder.install(module);
        return new HttpClientBindingBuilder(
                module,
                newSetBinder(binder, Key.get(HttpRequestFilter.class, annotation)),
                newSetBinder(binder, Key.get(HttpStatusListener.class, annotation)),
                newOptionalBinder(binder, Key.get(ByteBufferPool.class, annotation)));
    }

    public LinkedBindingBuilder<HttpRequestFilter> addGlobalFilterBinding()
    {
        return globalFilterBinder.addBinding();
    }

    public LinkedBindingBuilder<HttpStatusListener> addGlobalStatusListenerBinding()
    {
        return globalStatusListenerBinder.addBinding();
    }

    public HttpClientBinder bindGlobalFilter(Class<? extends HttpRequestFilter> filterClass)
    {
        globalFilterBinder.addBinding().to(filterClass).in(Scopes.SINGLETON);
        return this;
    }

    public HttpClientBinder bindByteBufferPool(ByteBufferPool byteBufferPool)
    {
        this.byteBufferPool.setBinding().toInstance(byteBufferPool);
        return this;
    }

    public HttpClientBinder bindGlobalFilter(HttpRequestFilter filter)
    {
        globalFilterBinder.addBinding().toInstance(filter);
        return this;
    }

    public static class HttpClientBindingBuilder
    {
        private final HttpClientModule module;
        private final Multibinder<HttpRequestFilter> filterBinder;
        private final Multibinder<HttpStatusListener> statusListenerBinder;
        private final OptionalBinder<ByteBufferPool> byteBufferPoolBinder;

        public HttpClientBindingBuilder(HttpClientModule module, Multibinder<HttpRequestFilter> filterBinder, Multibinder<HttpStatusListener> statusListenerBinder, OptionalBinder<ByteBufferPool> byteBufferPoolBinder)
        {
            this.module = requireNonNull(module, "module is null");
            this.filterBinder = requireNonNull(filterBinder, "multibinder is null");
            this.statusListenerBinder = requireNonNull(statusListenerBinder, "statusListenerBinder is null");
            this.byteBufferPoolBinder = requireNonNull(byteBufferPoolBinder, "byteBufferPoolBinder is null");
        }

        public HttpClientBindingBuilder withAlias(Class<? extends Annotation> alias)
        {
            module.addAlias(alias);
            return this;
        }

        public HttpClientBindingBuilder withAliases(Collection<Class<? extends Annotation>> aliases)
        {
            for (Class<? extends Annotation> annotation : aliases) {
                module.addAlias(annotation);
            }
            return this;
        }

        public HttpClientBindingBuilder withConfigDefaults(ConfigDefaults<HttpClientConfig> configDefaults)
        {
            module.withConfigDefaults(configDefaults);
            return this;
        }

        public LinkedBindingBuilder<HttpRequestFilter> addFilterBinding()
        {
            return filterBinder.addBinding();
        }

        public HttpClientBindingBuilder withFilter(Class<? extends HttpRequestFilter> filterClass)
        {
            filterBinder.addBinding().to(filterClass);
            return this;
        }

        public HttpClientBindingBuilder withByteBufferPool(Class<? extends ByteBufferPool> byteBufferPoolClass)
        {
            byteBufferPoolBinder.setBinding().to(byteBufferPoolClass);
            return this;
        }

        public HttpClientBindingBuilder withByteBufferPool(ByteBufferPool byteBufferPool)
        {
            byteBufferPoolBinder.setDefault().toInstance(byteBufferPool);
            return this;
        }

        public LinkedBindingBuilder<HttpStatusListener> addStatusListenerBinding()
        {
            return statusListenerBinder.addBinding();
        }

        public HttpClientBindingBuilder withStatusListener(Class<? extends HttpStatusListener> listenerClass)
        {
            addStatusListenerBinding().to(listenerClass);
            return this;
        }
    }
}
