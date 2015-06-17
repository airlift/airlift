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
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.google.inject.Scopes;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.http.client.balancing.BalancingHttpClient;
import com.proofpoint.http.client.balancing.BalancingHttpClientBindingBuilder;
import com.proofpoint.http.client.balancing.BalancingHttpClientConfig;
import com.proofpoint.http.client.balancing.ForBalancingHttpClient;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.reporting.ReportBinder.reportBinder;

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

    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String name, Class<? extends Annotation> annotation, Set<URI> baseUris)
    {
        checkNotNull(name, "name is null");
        checkNotNull(annotation, "annotation is null");
        checkNotNull(baseUris, "baseUris is null");
        checkArgument(!baseUris.isEmpty(), "baseUris is empty");

        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class).toProvider(new StaticHttpServiceBalancerProvider(annotation.getSimpleName(), baseUris));
        HttpClientBindingBuilder delegateBindingBuilder = httpClientPrivateBinder(privateBinder, binder).bindHttpClient(name, ForBalancingHttpClient.class);
        bindConfig(privateBinder).prefixedWith(name).to(BalancingHttpClientConfig.class);
        privateBinder.bind(HttpClient.class).annotatedWith(annotation).to(BalancingHttpClient.class).in(Scopes.SINGLETON);
        privateBinder.expose(HttpClient.class).annotatedWith(annotation);
        reportBinder(binder).export(HttpClient.class).annotatedWith(annotation).withGeneratedName();

        return new BalancingHttpClientBindingBuilder(binder, annotation, delegateBindingBuilder);
    }

    public BalancingHttpClientBindingBuilder bindBalancingHttpClient(String name, Class<? extends Annotation> annotation, Key<? extends HttpServiceBalancer> balancerKey)
    {
        checkNotNull(name, "name is null");
        checkNotNull(annotation, "annotation is null");
        checkNotNull(balancerKey, "balancerKey is null");

        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class).to(balancerKey);
        HttpClientBindingBuilder delegateBindingBuilder = httpClientPrivateBinder(privateBinder, binder).bindHttpClient(name, ForBalancingHttpClient.class);
        bindConfig(privateBinder).prefixedWith(name).to(BalancingHttpClientConfig.class);
        privateBinder.bind(HttpClient.class).annotatedWith(annotation).to(BalancingHttpClient.class).in(Scopes.SINGLETON);
        privateBinder.expose(HttpClient.class).annotatedWith(annotation);
        reportBinder(binder).export(HttpClient.class).annotatedWith(annotation).withGeneratedName();

        return new BalancingHttpClientBindingBuilder(binder, annotation, delegateBindingBuilder);
    }

    @SuppressWarnings("deprecation")
    private HttpClientBindingBuilder createBindingBuilder(HttpClientModule module)
    {
        binder.install(module);
        return new HttpClientAsyncBindingBuilder(module,
                newSetBinder(binder, HttpRequestFilter.class, module.getFilterQualifier()));
    }

    /**
     * @deprecated use {@link HttpClientBindingBuilder}
     */
    @Deprecated
    public static class HttpClientAsyncBindingBuilder
        extends HttpClientBindingBuilder
    {
        private HttpClientAsyncBindingBuilder(HttpClientModule module, Multibinder<HttpRequestFilter> multibinder)
        {
            super(module, multibinder);
        }
    }

    @SuppressWarnings("deprecation")
    public static class HttpClientBindingBuilder
    {
        private final HttpClientModule module;
        private final Multibinder<HttpRequestFilter> multibinder;

        private HttpClientBindingBuilder(HttpClientModule module, Multibinder<HttpRequestFilter> multibinder)
        {
            this.module = module;
            this.multibinder = multibinder;
        }

        public HttpClientAsyncBindingBuilder withAlias(Class<? extends Annotation> alias)
        {
            module.addAlias(alias);
            return (HttpClientAsyncBindingBuilder) this;
        }

        public HttpClientAsyncBindingBuilder withAliases(Collection<Class<? extends Annotation>> aliases)
        {
            for (Class<? extends Annotation> annotation : aliases) {
                module.addAlias(annotation);
            }
            return (HttpClientAsyncBindingBuilder) this;
        }

        public LinkedBindingBuilder<HttpRequestFilter> addFilterBinding()
        {
            return multibinder.addBinding();
        }

        public HttpClientAsyncBindingBuilder withFilter(Class<? extends HttpRequestFilter> filterClass)
        {
            multibinder.addBinding().to(filterClass);
            return (HttpClientAsyncBindingBuilder) this;
        }

        public HttpClientAsyncBindingBuilder withTracing()
        {
            return withFilter(TraceTokenRequestFilter.class);
        }

        public HttpClientAsyncBindingBuilder withPrivateIoThreadPool()
        {
            module.withPrivateIoThreadPool();
            return (HttpClientAsyncBindingBuilder) this;
        }
    }
}
