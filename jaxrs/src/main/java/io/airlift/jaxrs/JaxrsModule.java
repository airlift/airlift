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

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provides;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.http.server.TheServlet;
import io.airlift.jaxrs.tracing.JaxrsTracingModule;
import jakarta.servlet.Servlet;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.util.Objects.requireNonNull;

public class JaxrsModule
        extends AbstractConfigurationAwareModule
{
    public JaxrsModule() {}

    @Deprecated
    public JaxrsModule(boolean requireExplicitBindings)
    {
        checkArgument(requireExplicitBindings, "non-explicit bindings are no longer supported");
    }

    @Override
    protected void setup(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(Key.get(ServletContainer.class));
        jaxrsBinder(binder).bind(JsonMapper.class);
        jaxrsBinder(binder).bind(SmileMapper.class);
        jaxrsBinder(binder).bind(ParsingExceptionMapper.class);
        jaxrsBinder(binder).bind(FactoryBinder.class);

        newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();

        if (getProperty("tracing.enabled").map(Boolean::parseBoolean).orElse(false)) {
            install(new JaxrsTracingModule());
        }
    }

    @Provides
    public static ServletContainer createServletContainer(ResourceConfig resourceConfig)
    {
        return new ServletContainer(resourceConfig);
    }

    @Provides
    public static ResourceConfig createResourceConfig(@JaxrsResource Set<Object> jaxRsSingletons)
    {
        return new JaxrsResourceConfig(jaxRsSingletons);
    }

    @Provides
    @TheServlet
    public static Map<String, String> createTheServletParams()
    {
        return new HashMap<>();
    }

    public static class FactoryBinder
            extends AbstractBinder
    {
        private final Set<JerseyFactoryBinding> factories;

        @Inject
        private FactoryBinder(Set<JerseyFactoryBinding> factories)
        {
            this.factories = requireNonNull(factories, "factories is null");
        }

        @Override
        protected void configure()
        {
            factories.forEach(factoryBinding -> factoryBinding.bind(this));
        }
    }

    interface JerseyFactoryBinding
    {
        void bind(AbstractBinder binder);
    }
}
