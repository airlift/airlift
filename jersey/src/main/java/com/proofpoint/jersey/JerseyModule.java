package com.proofpoint.jersey;

import com.google.inject.servlet.ServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import java.util.HashMap;
import java.util.Map;

public class JerseyModule
    extends ServletModule
{
    @Override
    protected void configureServlets()
    {
        Map<String, String> params = new HashMap<String, String>();
        params.put("com.sun.jersey.spi.container.ContainerRequestFilters", OverrideMethodFilter.class.getName());

        serve("/*").with(GuiceContainer.class, params);
    }
}
