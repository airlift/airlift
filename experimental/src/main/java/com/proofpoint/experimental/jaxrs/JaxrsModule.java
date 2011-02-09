package com.proofpoint.experimental.jaxrs;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.jaxrs.OverrideMethodFilter;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.core.util.FeaturesAndProperties;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.sun.jersey.spi.MessageBodyWorkers;
import com.sun.jersey.spi.container.ExceptionMapperContext;
import com.sun.jersey.spi.container.WebApplication;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import javax.servlet.Servlet;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Providers;
import java.util.HashMap;
import java.util.Map;

import static org.codehaus.jackson.map.SerializationConfig.Feature.INDENT_OUTPUT;

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
    public JacksonJsonProvider createJacksonJsonProvider(final WebApplication webApplication)
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getDeserializationConfig().disable(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.getSerializationConfig().disable(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.getSerializationConfig().setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);

        return new PrettyJacksonJsonProvider(mapper, webApplication);
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
    public FeaturesAndProperties fearturesAndProperties(WebApplication webApplication)
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

    private static class PrettyJacksonJsonProvider extends JacksonJsonProvider
    {
        private final WebApplication webApplication;

        public PrettyJacksonJsonProvider(ObjectMapper mapper, WebApplication webApplication)
        {
            super(mapper);
            this.webApplication = webApplication;
        }

        @Override
        public ObjectMapper locateMapper(Class<?> type, MediaType mediaType)
        {
            ObjectMapper objectMapper = super.locateMapper(type, mediaType);
            if (isPrettyPrintRequested()) {
                objectMapper.getSerializationConfig().enable(INDENT_OUTPUT);
            }
            return objectMapper;
        }

        private boolean isPrettyPrintRequested()
        {
            HttpContext httpContext = webApplication.getThreadLocalHttpContext();
            if (httpContext == null) {
                return false;
            }
            UriInfo uriInfo = httpContext.getUriInfo();
            if (uriInfo == null) {
                return false;
            }
            MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
            if (queryParameters == null) {
                return false;
            }
            return queryParameters.containsKey("pretty");
        }
    }
}
