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
package com.proofpoint.jaxrs;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.core.util.FeaturesAndProperties;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.sun.jersey.spi.MessageBodyWorkers;
import com.sun.jersey.spi.container.ExceptionMapperContext;
import com.sun.jersey.spi.container.WebApplication;

import javax.ws.rs.ext.Providers;
import java.util.HashMap;
import java.util.Map;

public class JaxrsModule extends ServletModule
{
    @Override
    protected void configureServlets()
    {
        binder().requireExplicitBindings();
        binder().disableCircularProxies();

        Map<String, String> params = new HashMap<String, String>();
        params.put("com.sun.jersey.spi.container.ContainerRequestFilters", OverrideMethodFilter.class.getName());
        bind(GuiceContainer.class).in(Scopes.SINGLETON);
        serve("/*").with(Key.get(GuiceContainer.class), params);

        bind(JsonMapper.class).in(Scopes.SINGLETON);
    }

    @Provides
    public WebApplication webApp(GuiceContainer guiceContainer)
    {
        return guiceContainer.getWebApplication();
    }

    @Provides
    public Providers providers(WebApplication webApplication)
    {
        return webApplication.getProviders();
    }

    @Provides
    public FeaturesAndProperties featuresAndProperties(WebApplication webApplication)
    {
        return webApplication.getFeaturesAndProperties();
    }

    @Provides
    public MessageBodyWorkers messageBodyWorkers(WebApplication webApplication)
    {
        return webApplication.getMessageBodyWorkers();
    }

    @Provides
    public ExceptionMapperContext exceptionMapperContext(WebApplication webApplication)
    {
        return webApplication.getExceptionMapperContext();
    }
}
