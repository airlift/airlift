package com.proofpoint.jersey;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.proofpoint.jetty.TheServlet;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import javax.servlet.Servlet;
import java.util.HashMap;
import java.util.Map;

public class JerseyModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(GuiceContainer.class);
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
    public JacksonJsonProvider createJacksonJsonProvider()
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getDeserializationConfig().disable(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.getSerializationConfig().disable(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS);
        return new JacksonJsonProvider(mapper);
    }
}
