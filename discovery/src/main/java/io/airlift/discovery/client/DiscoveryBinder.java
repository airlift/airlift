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
package io.airlift.discovery.client;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import io.airlift.discovery.client.ServiceAnnouncement.ServiceAnnouncementBuilder;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static io.airlift.discovery.client.ServiceTypes.serviceType;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;

public class DiscoveryBinder
{
    public static DiscoveryBinder discoveryBinder(Binder binder)
    {
        requireNonNull(binder, "binder is null");
        return new DiscoveryBinder(binder);
    }

    private final Multibinder<ServiceSelector> serviceSelectorBinder;
    private final Multibinder<ServiceAnnouncement> serviceAnnouncementBinder;
    private final Binder binder;

    protected DiscoveryBinder(Binder binder)
    {
        requireNonNull(binder, "binder is null");
        this.binder = binder.skipSources(getClass());
        this.serviceSelectorBinder = newSetBinder(binder, ServiceSelector.class);
        this.serviceAnnouncementBinder = newSetBinder(binder, ServiceAnnouncement.class);
    }

    public void bindSelector(String type)
    {
        requireNonNull(type, "type is null");
        bindSelector(serviceType(type));
    }

    public void bindSelector(ServiceType serviceType)
    {
        requireNonNull(serviceType, "serviceType is null");

        configBinder(binder).bindConfig(ServiceSelectorConfig.class, serviceType, "discovery." + serviceType.value());

        Key<ServiceSelector> key = Key.get(ServiceSelector.class, serviceType);
        binder.bind(key).toProvider(new ServiceSelectorProvider(serviceType.value())).in(Scopes.SINGLETON);
        serviceSelectorBinder.addBinding().to(key).in(Scopes.SINGLETON);
    }

    public void bindServiceAnnouncement(ServiceAnnouncement announcement)
    {
        requireNonNull(announcement, "announcement is null");
        serviceAnnouncementBinder.addBinding().toInstance(announcement);
    }

    public void bindServiceAnnouncement(Provider<ServiceAnnouncement> announcementProvider)
    {
        requireNonNull(announcementProvider, "announcementProvider is null");
        serviceAnnouncementBinder.addBinding().toProvider(announcementProvider);
    }

    public <T extends ServiceAnnouncement> void bindServiceAnnouncement(Class<? extends Provider<T>> announcementProviderClass)
    {
        requireNonNull(announcementProviderClass, "announcementProviderClass is null");
        serviceAnnouncementBinder.addBinding().toProvider(announcementProviderClass);
    }

    public HttpAnnouncementBindingBuilder bindHttpAnnouncement(String type)
    {
        return bindHttpAnnouncement(type, null);
    }

    public HttpAnnouncementBindingBuilder bindHttpAnnouncement(String type, Class<? extends Annotation> bindingAnnotation)
    {
        HttpAnnouncement annotation = new HttpAnnouncementImpl(type + "." + randomUUID());
        MapBinder<String, String> propertiesBinder = newMapBinder(binder, String.class, String.class, annotation);
        bindServiceAnnouncement(new HttpAnnouncementProvider(type, annotation, Optional.ofNullable(bindingAnnotation)));
        return new HttpAnnouncementBindingBuilder(propertiesBinder);
    }

    public void bindHttpSelector(String type)
    {
        requireNonNull(type, "type is null");
        bindHttpSelector(serviceType(type));
    }

    public void bindHttpSelector(ServiceType serviceType)
    {
        requireNonNull(serviceType, "serviceType is null");
        bindSelector(serviceType);
        binder.bind(HttpServiceSelector.class).annotatedWith(serviceType).toProvider(new HttpServiceSelectorProvider(serviceType.value())).in(Scopes.SINGLETON);
    }

    public static class HttpAnnouncementBindingBuilder
    {
        private final MapBinder<String, String> propertiesBinder;

        public HttpAnnouncementBindingBuilder(MapBinder<String, String> propertiesBinder)
        {
            this.propertiesBinder = requireNonNull(propertiesBinder, "propertiesBinder is null");
        }

        @CanIgnoreReturnValue
        public HttpAnnouncementBindingBuilder addProperty(String key, String value)
        {
            requireNonNull(key, "key is null");
            requireNonNull(value, "value is null");
            propertiesBinder.addBinding(key).toInstance(value);
            return this;
        }

        @CanIgnoreReturnValue
        public HttpAnnouncementBindingBuilder addProperties(Map<String, String> properties)
        {
            properties.forEach(this::addProperty);
            return this;
        }

        @CanIgnoreReturnValue
        public HttpAnnouncementBindingBuilder bindPropertyProvider(String key, Provider<String> provider)
        {
            requireNonNull(key, "key is null");
            requireNonNull(provider, "provider is null");
            propertiesBinder.addBinding(key).toProvider(provider);
            return this;
        }

        @CanIgnoreReturnValue
        public HttpAnnouncementBindingBuilder bindPropertyProvider(String key, Class<? extends Provider<String>> providerType)
        {
            return bindPropertyProvider(key, Key.get(providerType));
        }

        @CanIgnoreReturnValue
        public HttpAnnouncementBindingBuilder bindPropertyProvider(String key, Key<? extends Provider<String>> providerKey)
        {
            requireNonNull(key, "key is null");
            requireNonNull(providerKey, "providerKey is null");
            propertiesBinder.addBinding(key).toProvider(providerKey);
            return this;
        }
    }

    static class HttpAnnouncementProvider
            implements Provider<ServiceAnnouncement>
    {
        private final String type;
        private final Annotation annotation;
        private final Optional<Class<? extends Annotation>> announcementAnnotation;
        private Injector injector;

        public HttpAnnouncementProvider(String type, Annotation annotation, Optional<Class<? extends Annotation>> announcementAnnotation)
        {
            this.type = requireNonNull(type, "type is null");
            this.annotation = requireNonNull(annotation, "annotation is null");
            this.announcementAnnotation = requireNonNull(announcementAnnotation, "announcementAnnotation is null");
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @Override
        public ServiceAnnouncement get()
        {
            ServiceAnnouncementBuilder builder = serviceAnnouncement(type);
            builder.addProperties(injector.getInstance(Key.get(new TypeLiteral<>() {}, annotation)));

            AnnouncementHttpServerInfo httpServerInfo = injector.getInstance(qualifiedKey(announcementAnnotation, AnnouncementHttpServerInfo.class));

            if (httpServerInfo.getHttpUri() != null) {
                builder.addProperty("http", httpServerInfo.getHttpUri().toString());
                builder.addProperty("http-external", httpServerInfo.getHttpExternalUri().toString());
            }
            if (httpServerInfo.getHttpsUri() != null) {
                builder.addProperty("https", httpServerInfo.getHttpsUri().toString());
                builder.addProperty("https-external", httpServerInfo.getHttpsExternalUri().toString());
            }
            return builder.build();
        }
    }

    static <T> Key<T> qualifiedKey(Optional<Class<? extends Annotation>> qualifier, Class<T> type)
    {
        return qualifier
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }
}
