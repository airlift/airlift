/*
 * Copyright 2012 Proofpoint, Inc.
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
package com.proofpoint.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.proofpoint.http.server.TheAdminServlet;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.log.Logger;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerLifecycleListener;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.Servlet;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;

public class JaxrsModule
        implements Module
{
    private static final Logger log = Logger.get(JaxrsModule.class);

    private final boolean requireExplicitBindings;
    private final AtomicReference<ServiceLocator> locatorReference = new AtomicReference<>();

    public JaxrsModule()
    {
        this(false);
    }

    private JaxrsModule(boolean requireExplicitBindings)
    {
        this.requireExplicitBindings = requireExplicitBindings;
    }

    public static JaxrsModule explicitJaxrsModule() {
        return new JaxrsModule(true);
    }

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(Key.get(ServletContainer.class));
        jaxrsBinder(binder).bind(JsonMapper.class);
        jaxrsBinder(binder).bind(SmileMapper.class);
        jaxrsBinder(binder).bind(ParsingExceptionMapper.class);
        jaxrsBinder(binder).bind(QueryParamExceptionMapper.class);
        jaxrsBinder(binder).bind(OverrideMethodFilter.class);
        jaxrsBinder(binder).bind(TimingResourceDynamicFeature.class);
        jaxrsBinder(binder).bindAdmin(ParsingExceptionMapper.class);
        jaxrsBinder(binder).bindAdmin(QueryParamExceptionMapper.class);
        jaxrsBinder(binder).bindAdmin(OverrideMethodFilter.class);

        newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
        newSetBinder(binder, JaxrsBinding.class, JaxrsResource.class).permitDuplicates();
        newMapBinder(binder, new TypeLiteral<Class<?>>() {}, new TypeLiteral<Supplier<?>>() {}, JaxrsInjectionProvider.class);
    }

    @Provides
    public static ServletContainer createServletContainer(ResourceConfig resourceConfig)
    {
        return new ServletContainer(resourceConfig);
    }

    @Provides
    public ResourceConfig createResourceConfig(Application application, @JaxrsInjectionProvider final Map<Class<?>, Supplier<?>> supplierMap)
    {
        ResourceConfig config = ResourceConfig.forApplication(application);
        config.register(MultiPartFeature.class);

        config.register(new ContainerLifecycleListener()
        {
            @Override
            public void onStartup(Container container)
            {
                locatorReference.set(container.getApplicationHandler().getServiceLocator());
            }

            @Override
            public void onReload(Container container)
            {
            }

            @Override
            public void onShutdown(Container container)
            {
            }
        });

        config.register(new AbstractBinder()
        {
            @Override
            protected void configure()
            {
                for (final Entry<Class<?>, Supplier<?>> entry : supplierMap.entrySet()) {
                    bindSupplier(entry.getKey(), entry.getValue());
                }
            }

            @SuppressWarnings("unchecked")
            private <T> void bindSupplier(Class<T> type, Supplier<?> supplier)
            {
                bindFactory(new InjectionProviderFactory<>(type, (Supplier<T>) supplier, locatorReference)).to(type);
            }
        });

        return config;
    }

    @Provides
    public Application createJaxRsApplication(@JaxrsResource Set<Object> jaxRsSingletons, @JaxrsResource Set<JaxrsBinding> jaxrsBinding, Injector injector)
    {
        // detect jax-rs services that are bound into Guice, but not explicitly exported
        Set<Key<?>> missingBindings = new HashSet<>();
        ImmutableSet.Builder<Object> singletons = ImmutableSet.builder();
        singletons.addAll(jaxRsSingletons);
        while (injector != null) {
            for (Entry<Key<?>, Binding<?>> entry : injector.getBindings().entrySet()) {
                Key<?> key = entry.getKey();
                if (isJaxRsBinding(key) && !jaxrsBinding.contains(new JaxrsBinding(key))) {
                    if (requireExplicitBindings) {
                        missingBindings.add(key);
                    }
                    else {
                        log.warn("Jax-rs service %s is not explicitly bound using the JaxRsBinder", key);
                        Object jaxRsSingleton = entry.getValue().getProvider().get();
                        singletons.add(jaxRsSingleton);
                    }
                }
            }
            injector = injector.getParent();
        }
        checkState(!requireExplicitBindings || missingBindings.isEmpty(), "Jax-rs services must be explicitly bound using the JaxRsBinder: ", missingBindings);

        return new JaxRsApplication(singletons.build());
    }

    @Provides
    @TheServlet
    public static Map<String, String> createTheServletParams()
    {
        return new HashMap<>();
    }

    private static boolean isJaxRsBinding(Key<?> key)
    {
        Type type = key.getTypeLiteral().getType();
        if (!(type instanceof Class)) {
            return false;
        }
        return isJaxRsType((Class<?>) type);
    }

    @Provides
    @TheAdminServlet
    public static Servlet createTheAdminServlet(@AdminJaxrsResource Set<Object> adminJaxRsSingletons, ObjectMapper objectMapper) {
        // The admin servlet needs its own JsonMapper object so that it references
        // the admin port's UriInfo
        ImmutableSet.Builder<Object> singletons = ImmutableSet.builder();
        singletons.addAll(adminJaxRsSingletons);
        singletons.add(new JsonMapper(objectMapper));

        Application application = new JaxRsApplication(singletons.build());
        return new ServletContainer(ResourceConfig.forApplication(application));
    }

    private static boolean isJaxRsType(Class<?> type)
    {
        if (type == null) {
            return false;
        }

        if (type.isAnnotationPresent(Provider.class)) {
            return true;
        }
        else if (type.isAnnotationPresent(Path.class)) {
            return true;
        }
        if (isJaxRsType(type.getSuperclass())) {
            return true;
        }
        for (Class<?> typeInterface : type.getInterfaces()) {
            if (isJaxRsType(typeInterface)) {
                return true;
            }
        }

        return false;
    }

    private static class JaxRsApplication
            extends Application
    {
        private final Set<Object> jaxRsSingletons;

        JaxRsApplication(Set<Object> jaxRsSingletons)
        {
            this.jaxRsSingletons = jaxRsSingletons;
        }

        @Override
        public Set<Object> getSingletons()
        {
            return jaxRsSingletons;
        }
    }

    private static class InjectionProviderFactory<T> implements Factory<T>
    {
        private final Supplier<? extends T> supplier;
        private final AtomicReference<ServiceLocator> locatorReference;

        InjectionProviderFactory(Class<T> type, Supplier<? extends T> supplier, AtomicReference<ServiceLocator> locatorReference)
        {
            this.supplier = supplier;
            this.locatorReference = locatorReference;
        }

        @Override
        public T provide()
        {
            T object = supplier.get();
            ServiceLocator locator = locatorReference.get();
            locator.inject(object);
            locator.postConstruct(object);
            return object;
        }

        @Override
        public void dispose(T o)
        {
            locatorReference.get().preDestroy(o);
        }
    }
}
