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
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;

import java.lang.annotation.Annotation;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Beta
public class HttpClientBinder
{
    private final Binder binder;
    private final Binder rootBinder;

    private HttpClientBinder(Binder binder, Binder rootBinder)
    {
        this.binder = checkNotNull(binder, "binder is null");
        this.rootBinder = checkNotNull(rootBinder, "rootBinder is null");
    }

    public static HttpClientBinder httpClientBinder(Binder binder)
    {
        return new HttpClientBinder(binder, binder);
    }

    public static HttpClientBinder httpClientPrivateBinder(Binder privateBinder, Binder rootBinder)
    {
        return new HttpClientBinder(privateBinder, rootBinder);
    }

    public HttpClientBindingBuilder bindHttpClient(String name, Class<? extends Annotation> annotation)
    {
        checkNotNull(name, "name is null");
        checkNotNull(annotation, "annotation is null");
        return createBindingBuilder(new HttpClientModule(name, annotation, rootBinder));
    }

    public HttpClientAsyncBindingBuilder bindAsyncHttpClient(String name, Class<? extends Annotation> annotation)
    {
        checkNotNull(name, "name is null");
        checkNotNull(annotation, "annotation is null");
        return createAsyncBindingBuilder(new AsyncHttpClientModule(name, annotation, rootBinder));
    }

    private HttpClientBindingBuilder createBindingBuilder(AbstractHttpClientModule module)
    {
        binder.install(module);
        return new HttpClientBindingBuilder(module,
                newSetBinder(binder, HttpRequestFilter.class, module.getFilterQualifier()));
    }

    private HttpClientAsyncBindingBuilder createAsyncBindingBuilder(AsyncHttpClientModule module)
    {
        binder.install(module);
        return new HttpClientAsyncBindingBuilder(module,
                newSetBinder(binder, HttpRequestFilter.class, module.getFilterQualifier()));
    }

    public static class HttpClientBindingBuilder
        extends AbstractHttpClientBindingBuilder<HttpClientBindingBuilder>
    {
        private HttpClientBindingBuilder(AbstractHttpClientModule module, Multibinder<HttpRequestFilter> multibinder)
        {
            super(module, multibinder);
        }
    }

    public static class HttpClientAsyncBindingBuilder
    extends AbstractHttpClientBindingBuilder<HttpClientAsyncBindingBuilder>
    {
        private final AsyncHttpClientModule module;

        private HttpClientAsyncBindingBuilder(AsyncHttpClientModule module, Multibinder<HttpRequestFilter> multibinder)
        {
            super(module, multibinder);
            this.module = module;
        }

        public HttpClientAsyncBindingBuilder withPrivateIoThreadPool()
        {
            module.withPrivateIoThreadPool();
            return this;
        }
    }

    protected abstract static class AbstractHttpClientBindingBuilder<T extends AbstractHttpClientBindingBuilder<T>>
    {
        protected final AbstractHttpClientModule module;
        private final Multibinder<HttpRequestFilter> multibinder;

        private AbstractHttpClientBindingBuilder(AbstractHttpClientModule module, Multibinder<HttpRequestFilter> multibinder)
        {
            this.module = module;
            this.multibinder = multibinder;
        }

        @SuppressWarnings("unchecked")
        public T withAlias(Class<? extends Annotation> alias)
        {
            module.addAlias(alias);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T withAliases(Collection<Class<? extends Annotation>> aliases)
        {
            for (Class<? extends Annotation> annotation : aliases) {
                module.addAlias(annotation);
            }
            return (T) this;
        }

        public LinkedBindingBuilder<HttpRequestFilter> addFilterBinding()
        {
            return multibinder.addBinding();
        }

        @SuppressWarnings("unchecked")
        public T withFilter(Class<? extends HttpRequestFilter> filterClass)
        {
            multibinder.addBinding().to(filterClass);
            return (T) this;
        }

        public T withTracing()
        {
            return withFilter(TraceTokenRequestFilter.class);
        }
    }
}
