package com.proofpoint.jersey;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.proofpoint.jetty.TheServlet;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

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
}
