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
package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement.ServiceAnnouncementBuilder;
import com.proofpoint.discovery.client.balancing.HttpServiceBalancerProvider;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpClientBinder.AbstractHttpClientBindingBuilder;
import com.proofpoint.http.client.HttpClientBinder.HttpClientAsyncBindingBuilder;
import com.proofpoint.http.client.HttpClientBinder.HttpClientBindingBuilder;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.balancing.BalancingAsyncHttpClient;
import com.proofpoint.http.client.balancing.BalancingHttpClient;
import com.proofpoint.http.client.balancing.BalancingHttpClientConfig;
import com.proofpoint.http.client.balancing.ForBalancingHttpClient;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;

import java.lang.annotation.Annotation;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.http.client.HttpClientBinder.httpClientPrivateBinder;
import static com.proofpoint.reporting.ReportBinder.reportBinder;

public class DiscoveryBinder
{
    public static DiscoveryBinder discoveryBinder(Binder binder)
    {
        checkNotNull(binder, "binder is null");
        return new DiscoveryBinder(binder);
    }

    private final Multibinder<ServiceAnnouncement> serviceAnnouncementBinder;
    private final Binder binder;

    protected DiscoveryBinder(Binder binder)
    {
        checkNotNull(binder, "binder is null");
        this.binder = binder;
        this.serviceAnnouncementBinder = Multibinder.newSetBinder(binder, ServiceAnnouncement.class);
    }

    public void bindSelector(String type)
    {
        checkNotNull(type, "type is null");
        bindSelector(serviceType(type));
    }

    public void bindSelector(ServiceType serviceType)
    {
        checkNotNull(serviceType, "serviceType is null");
        bindConfig(binder).annotatedWith(serviceType).prefixedWith("discovery." + serviceType.value()).to(ServiceSelectorConfig.class);
        binder.bind(ServiceSelector.class).annotatedWith(serviceType).toProvider(new ServiceSelectorProvider(serviceType.value())).in(Scopes.SINGLETON);
    }

    public void bindHttpBalancer(String type)
    {
        checkNotNull(type, "type is null");
        bindHttpBalancer(serviceType(type));
    }

    public void bindHttpBalancer(ServiceType serviceType)
    {
        checkNotNull(serviceType, "serviceType is null");
        bindConfig(binder).annotatedWith(serviceType).prefixedWith("discovery." + serviceType.value()).to(ServiceSelectorConfig.class);
        binder.bind(HttpServiceBalancer.class).annotatedWith(serviceType).toProvider(new HttpServiceBalancerProvider(serviceType.value())).in(Scopes.SINGLETON);
    }

    public void bindServiceAnnouncement(ServiceAnnouncement announcement)
    {
        checkNotNull(announcement, "announcement is null");
        serviceAnnouncementBinder.addBinding().toInstance(announcement);
    }

    public void bindServiceAnnouncement(Provider<ServiceAnnouncement> announcementProvider)
    {
        checkNotNull(announcementProvider, "announcementProvider is null");
        serviceAnnouncementBinder.addBinding().toProvider(announcementProvider);
    }

    public <T extends ServiceAnnouncement> void bindServiceAnnouncement(Class<? extends Provider<T>> announcementProviderClass)
    {
        checkNotNull(announcementProviderClass, "announcementProviderClass is null");
        serviceAnnouncementBinder.addBinding().toProvider(announcementProviderClass);
    }

    public ServiceAnnouncementBuilder bindHttpAnnouncement(String type)
    {
        ServiceAnnouncementBuilder serviceAnnouncementBuilder = serviceAnnouncement(type);
        bindServiceAnnouncement(new HttpAnnouncementProvider(serviceAnnouncementBuilder));
        return serviceAnnouncementBuilder;
    }

    public void bindHttpSelector(String type)
    {
        checkNotNull(type, "type is null");
        bindHttpSelector(serviceType(type));
    }

    public void bindHttpSelector(ServiceType serviceType)
    {
        checkNotNull(serviceType, "serviceType is null");
        bindSelector(serviceType);
        binder.bind(HttpServiceSelector.class).annotatedWith(serviceType).toProvider(new HttpServiceSelectorProvider(serviceType.value())).in(Scopes.SINGLETON);
    }

    public BalancingHttpClientBindingBuilder bindDiscoveredHttpClient(String type, Class<? extends Annotation> annotation)
    {
        return bindDiscoveredHttpClient(serviceType(checkNotNull(type, "type is null")), annotation);
    }

    public BalancingHttpClientBindingBuilder bindDiscoveredHttpClient(ServiceType serviceType, Class<? extends Annotation> annotation)
    {
        checkNotNull(serviceType, "serviceType is null");
        checkNotNull(annotation, "annotation is null");

        bindHttpBalancer(serviceType);
        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class).to(Key.get(HttpServiceBalancer.class, serviceType));
        HttpClientBindingBuilder delegateBindingBuilder = httpClientPrivateBinder(privateBinder, binder).bindHttpClient(serviceType.value(), ForBalancingHttpClient.class);
        bindConfig(privateBinder).prefixedWith(serviceType.value()).to(BalancingHttpClientConfig.class);
        privateBinder.bind(HttpClient.class).annotatedWith(annotation).to(BalancingHttpClient.class).in(Scopes.SINGLETON);
        privateBinder.expose(HttpClient.class).annotatedWith(annotation);
        reportBinder(binder).export(HttpClient.class).annotatedWith(annotation).withGeneratedName();

        return new BalancingHttpClientBindingBuilder(binder, annotation, delegateBindingBuilder);
    }

    public BalancingHttpClientAsyncBindingBuilder bindDiscoveredAsyncHttpClient(String type, Class<? extends Annotation> annotation)
    {
        return bindDiscoveredAsyncHttpClient(serviceType(checkNotNull(type, "type is null")), annotation);
    }

    public BalancingHttpClientAsyncBindingBuilder bindDiscoveredAsyncHttpClient(ServiceType serviceType, Class<? extends Annotation> annotation)
    {
        bindHttpBalancer(serviceType);
        return bindDiscoveredAsyncHttpClientWithBalancer(serviceType, annotation);
    }

    BalancingHttpClientAsyncBindingBuilder bindDiscoveredAsyncHttpClientWithBalancer(ServiceType serviceType, Class<? extends Annotation> annotation)
    {
        checkNotNull(serviceType, "serviceType is null");
        checkNotNull(annotation, "annotation is null");

        PrivateBinder privateBinder = binder.newPrivateBinder();
        privateBinder.bind(HttpServiceBalancer.class).annotatedWith(ForBalancingHttpClient.class).to(Key.get(HttpServiceBalancer.class, serviceType));
        HttpClientAsyncBindingBuilder delegateBindingBuilder = httpClientPrivateBinder(privateBinder, binder).bindAsyncHttpClient(serviceType.value(), ForBalancingHttpClient.class);
        bindConfig(privateBinder).prefixedWith(serviceType.value()).to(BalancingHttpClientConfig.class);
        privateBinder.bind(AsyncHttpClient.class).annotatedWith(annotation).to(BalancingAsyncHttpClient.class).in(Scopes.SINGLETON);
        privateBinder.expose(AsyncHttpClient.class).annotatedWith(annotation);
        reportBinder(binder).export(AsyncHttpClient.class).annotatedWith(annotation).withGeneratedName();

        return new BalancingHttpClientAsyncBindingBuilder(binder, annotation, delegateBindingBuilder);
    }

    static class HttpAnnouncementProvider implements Provider<ServiceAnnouncement>
    {
        private final ServiceAnnouncementBuilder builder;
        private AnnouncementHttpServerInfo httpServerInfo;

        public HttpAnnouncementProvider(ServiceAnnouncementBuilder serviceAnnouncementBuilder)
        {
            builder = serviceAnnouncementBuilder;
        }

        @Inject
        public void setAnnouncementHttpServerInfo(AnnouncementHttpServerInfo httpServerInfo)
        {
            this.httpServerInfo = httpServerInfo;
        }

        @Override
        public ServiceAnnouncement get()
        {
            if (httpServerInfo.getHttpUri() != null) {
                builder.addProperty("http", httpServerInfo.getHttpUri().toString());
                builder.addProperty("http-external", httpServerInfo.getHttpExternalUri().toString());
            }
            if (httpServerInfo.getHttpsUri() != null) {
                builder.addProperty("https", httpServerInfo.getHttpsUri().toString());
            }
            return builder.build();
        }
    }

    public static class BalancingHttpClientBindingBuilder
            extends AbstractBalancingHttpClientBindingBuilder<HttpClient, BalancingHttpClientBindingBuilder, HttpClientAsyncBindingBuilder>
    {
        public BalancingHttpClientBindingBuilder(Binder binder, Class<? extends Annotation> annotation, HttpClientBindingBuilder delegateBindingBuilder)
        {
            super(binder, HttpClient.class, annotation, delegateBindingBuilder);
        }
    }

    public static class BalancingHttpClientAsyncBindingBuilder
            extends AbstractBalancingHttpClientBindingBuilder<AsyncHttpClient, BalancingHttpClientAsyncBindingBuilder, HttpClientAsyncBindingBuilder>
    {
        public BalancingHttpClientAsyncBindingBuilder(Binder binder, Class<? extends Annotation> annotation, HttpClientAsyncBindingBuilder delegateBindingBuilder)
        {
            super(binder, AsyncHttpClient.class, annotation, delegateBindingBuilder);
        }

        public BalancingHttpClientAsyncBindingBuilder withPrivateIoThreadPool()
        {
            super.delegateBindingBuilder.withPrivateIoThreadPool();
            return this;
        }
    }

    protected abstract static class AbstractBalancingHttpClientBindingBuilder<T, B extends AbstractBalancingHttpClientBindingBuilder<T, B, D>, D extends AbstractHttpClientBindingBuilder<D>>
    {
        private final Binder binder;
        private final Class<T> aClass;
        private final Class<? extends Annotation> annotation;
        protected final D delegateBindingBuilder;

        public AbstractBalancingHttpClientBindingBuilder(Binder binder, Class<T> aClass, Class<? extends Annotation> annotation, D delegateBindingBuilder)
        {
            this.binder = binder;
            this.aClass = aClass;
            this.annotation = annotation;
            this.delegateBindingBuilder = delegateBindingBuilder;
        }

        @SuppressWarnings("unchecked")
        public B withAlias(Class<? extends Annotation> alias)
        {
            binder.bind(aClass).annotatedWith(alias).to(Key.get(aClass, annotation));
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B withAliases(ImmutableList<Class<? extends Annotation>> aliases)
        {
            for (Class<? extends Annotation> alias : aliases) {
                binder.bind(aClass).annotatedWith(alias).to(Key.get(aClass, annotation));
            }
            return (B) this;
        }

        public LinkedBindingBuilder<HttpRequestFilter> addFilterBinding()
        {
            return delegateBindingBuilder.addFilterBinding();
        }

        @SuppressWarnings("unchecked")
        public B withFilter(Class<? extends HttpRequestFilter> filterClass)
        {
            delegateBindingBuilder.withFilter(filterClass);
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B withTracing()
        {
            delegateBindingBuilder.withTracing();
            return (B) this;
        }
    }
}
