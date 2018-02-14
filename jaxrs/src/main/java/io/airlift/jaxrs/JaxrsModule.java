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
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import io.airlift.http.server.TheServlet;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.plugins.server.servlet.ServletBootstrap;
import org.jboss.resteasy.plugins.server.servlet.ServletContainerDispatcher;
import org.jboss.resteasy.spi.Dispatcher;

import javax.inject.Singleton;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

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

        jaxrsBinder(binder).bind(JsonMapper.class);
        jaxrsBinder(binder).bind(SmileMapper.class);
        jaxrsBinder(binder).bind(ParsingExceptionMapper.class);
        jaxrsBinder(binder).bind(OverrideMethodFilter.class);

        newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
    }

    @Provides
    @Singleton
    @TheServlet
    public Servlet createJaxRsApplication(@JaxrsResource Set<Object> objects, Injector injector)
    {
        Set<Object> resources = new HashSet<>();
        Set<Object> providers = new HashSet<>();
        for (Object object : objects) {
            Class<?> type = object.getClass();
            if (isJaxRsType(type, Path.class)) {
                resources.add(object);
            }
            else if (isJaxRsType(type, Provider.class)) {
                providers.add(object);
            }
            else {
                throw new ProvisionException("Invalid JAX-RS type: " + type);
            }
        }

        return new HttpServlet30Dispatcher()
        {
            @Override
            public void init(ServletConfig servletConfig)
                    throws ServletException
            {
                super.init(servletConfig);
                ServletBootstrap bootstrap = new ServletBootstrap(servletConfig);
                servletContainerDispatcher = new ServletContainerDispatcher();
                servletContainerDispatcher.init(servletConfig.getServletContext(), bootstrap, this, this);

                Dispatcher dispatcher = servletContainerDispatcher.getDispatcher();
                resources.forEach(dispatcher.getRegistry()::addSingletonResource);
                providers.forEach(dispatcher.getProviderFactory()::register);
                dispatcher.getDefaultContextObjects().put(ServletConfig.class, servletConfig);
            }
        };
    }

    @Provides
    @TheServlet
    public static Map<String, String> createTheServletParams()
    {
        Map<String, String> initParams = new HashMap<>();
        return initParams;
    }

    private static boolean isJaxRsType(Class<?> type, Class<? extends Annotation> annotation)
    {
        if (type == null) {
            return false;
        }

        if (type.isAnnotationPresent(annotation)) {
            return true;
        }
        if (isJaxRsType(type.getSuperclass(), annotation)) {
            return true;
        }
        for (Class<?> typeInterface : type.getInterfaces()) {
            if (isJaxRsType(typeInterface, annotation)) {
                return true;
            }
        }

        return false;
    }
}
