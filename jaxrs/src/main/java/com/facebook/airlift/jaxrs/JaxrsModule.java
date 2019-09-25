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
package com.facebook.airlift.jaxrs;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.facebook.airlift.http.server.TheServlet;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.inject.Inject;
import javax.servlet.Servlet;
import javax.ws.rs.core.Application;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.facebook.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public class JaxrsModule
        implements Module
{
    public JaxrsModule() {}

    @Deprecated
    public JaxrsModule(boolean requireExplicitBindings)
    {
        checkArgument(requireExplicitBindings, "non-explicit bindings are no longer supported");
    }

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(Application.class).to(JaxRsApplication.class).in(Scopes.SINGLETON);
        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(Key.get(ServletContainer.class));
        jaxrsBinder(binder).bind(JsonMapper.class);
        jaxrsBinder(binder).bind(SmileMapper.class);
        jaxrsBinder(binder).bind(ParsingExceptionMapper.class);
        jaxrsBinder(binder).bind(OverrideMethodFilter.class);

        newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
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
    @TheServlet
    public static Map<String, String> createTheServletParams()
    {
        Map<String, String> initParams = new HashMap<>();
        return initParams;
    }

    public static class JaxRsApplication
            extends Application
    {
        private final Set<Object> jaxRsSingletons;

        @Inject
        public JaxRsApplication(@JaxrsResource Set<Object> jaxRsSingletons)
        {
            this.jaxRsSingletons = ImmutableSet.copyOf(jaxRsSingletons);
        }

        @Override
        public Set<Object> getSingletons()
        {
            return jaxRsSingletons;
        }
    }
}
