package com.proofpoint.jersey;

import com.proofpoint.jetty.TheServlet;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.google.inject.Module;
import com.google.inject.Binder;

import javax.servlet.Servlet;

public class JerseyModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(GuiceContainer.class);
    }
}
