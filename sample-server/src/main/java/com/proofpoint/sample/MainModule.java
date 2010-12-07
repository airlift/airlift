package com.proofpoint.sample;

import com.proofpoint.sample.HelloConfig;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.sample.HelloResource;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;

public class MainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.bind(HelloResource.class);
        binder.bind(JacksonJsonProvider.class).in(Scopes.SINGLETON);

        ConfigurationModule.bindConfig(binder, HelloConfig.class);
    }
}
