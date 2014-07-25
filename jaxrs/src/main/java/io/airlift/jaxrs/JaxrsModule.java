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
package io.airlift.jaxrs;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.airlift.http.server.TheServlet;
import io.airlift.log.Logger;
import org.glassfish.jersey.server.ResourceConfig;
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public class JaxrsModule
        implements Module
{
    private static final Logger log = Logger.get(JaxrsModule.class);

    private final boolean requireExplicitBindings;

    public JaxrsModule()
    {
        this(false);
    }

    public JaxrsModule(boolean requireExplicitBindings)
    {
        this.requireExplicitBindings = requireExplicitBindings;
    }

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(Key.get(ServletContainer.class));
        jaxrsBinder(binder).bind(JsonMapper.class);
        jaxrsBinder(binder).bind(SmileMapper.class);
        jaxrsBinder(binder).bind(ParsingExceptionMapper.class);
        jaxrsBinder(binder).bind(OverrideMethodFilter.class);

        newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
        newSetBinder(binder, JaxrsBinding.class, JaxrsResource.class).permitDuplicates();
    }

    @Provides
    public static ServletContainer createServletContainer(ResourceConfig resourceConfig)
    {
        return new ServletContainer(resourceConfig);
    }

    @Provides
    public static ResourceConfig createResourceConfig(Application application)
    {
        return ResourceConfig.forApplication(application);
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
                        log.warn("Jax-rs service %s is not explicitly bound using the JaxrsBinder", key);
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
        Map<String, String> initParams = new HashMap<>();
        return initParams;
    }

    private static boolean isJaxRsBinding(Key<?> key)
    {
        Type type = key.getTypeLiteral().getType();
        if (!(type instanceof Class)) {
            return false;
        }
        return isJaxRsType((Class<?>) type);
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

        public JaxRsApplication(Set<Object> jaxRsSingletons)
        {
            this.jaxRsSingletons = jaxRsSingletons;
        }

        @Override
        public Set<Object> getSingletons()
        {
            return jaxRsSingletons;
        }
    }
}
