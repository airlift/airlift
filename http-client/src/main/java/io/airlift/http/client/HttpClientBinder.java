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
import com.google.inject.Binder;
import com.google.inject.Scopes;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import io.airlift.configuration.ConfigDefaults;

import java.lang.annotation.Annotation;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Beta
public class HttpClientBinder
{
    private final Binder binder;
    private final Multibinder<HttpRequestFilter> globalFilterBinder;

    private HttpClientBinder(Binder binder)
    {
        this.binder = checkNotNull(binder, "binder is null").skipSources(getClass());
        this.globalFilterBinder = newSetBinder(binder, HttpRequestFilter.class, GlobalFilter.class);
    }

    public static HttpClientBinder httpClientBinder(Binder binder)
    {
        return new HttpClientBinder(binder);
    }

    public HttpClientBindingBuilder bindHttpClient(String name, Class<? extends Annotation> annotation)
    {
        HttpClientModule module = new HttpClientModule(name, annotation);
        binder.install(module);
        return new HttpClientBindingBuilder(module, newSetBinder(binder, HttpRequestFilter.class, annotation));
    }

    public LinkedBindingBuilder<HttpRequestFilter> addGlobalFilterBinding()
    {
        return globalFilterBinder.addBinding();
    }

    public HttpClientBinder bindGlobalFilter(Class<? extends HttpRequestFilter> filterClass)
    {
        globalFilterBinder.addBinding().to(filterClass).in(Scopes.SINGLETON);
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
        private final Multibinder<HttpRequestFilter> multibinder;

        public HttpClientBindingBuilder(HttpClientModule module, Multibinder<HttpRequestFilter> multibinder)
        {
            this.module = module;
            this.multibinder = multibinder;
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
            return multibinder.addBinding();
        }

        public HttpClientBindingBuilder withFilter(Class<? extends HttpRequestFilter> filterClass)
        {
            multibinder.addBinding().to(filterClass);
            return this;
        }

        public HttpClientBindingBuilder withTracing()
        {
            return withFilter(TraceTokenRequestFilter.class);
        }

        public HttpClientBindingBuilder withPrivateIoThreadPool()
        {
            module.withPrivateIoThreadPool();
            return this;
        }
    }
}
