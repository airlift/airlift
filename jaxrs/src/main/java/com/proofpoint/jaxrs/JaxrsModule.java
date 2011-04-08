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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.proofpoint.http.server.TheServlet;
import com.sun.jersey.core.util.FeaturesAndProperties;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.sun.jersey.spi.MessageBodyWorkers;
import com.sun.jersey.spi.container.ExceptionMapperContext;
import com.sun.jersey.spi.container.WebApplication;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import javax.servlet.Servlet;
import javax.ws.rs.ext.Providers;
import java.util.HashMap;
import java.util.Map;

public class JaxrsModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(GuiceContainer.class).in(Scopes.SINGLETON);
        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(Key.get(GuiceContainer.class));
    }

    @Provides
    @TheServlet
    public Map<String, String> createTheServletParams()
    {
        Map<String, String> initParams = new HashMap<String, String>();
        initParams.put("com.sun.jersey.spi.container.ContainerRequestFilters", OverrideMethodFilter.class.getName());

        return initParams;
    }


    @Provides
    @Singleton
    public JsonMapper createJsonMapper(final WebApplication webApplication)
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getDeserializationConfig().disable(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.getSerializationConfig().disable(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.getSerializationConfig().setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);

        return new JsonMapper(mapper, webApplication);
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
