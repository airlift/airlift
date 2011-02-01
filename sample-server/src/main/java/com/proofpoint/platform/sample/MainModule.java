package com.proofpoint.platform.sample;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import org.weakref.jmx.guice.MBeanModule;

public class MainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.bind(PersonStore.class).in(Scopes.SINGLETON);
        MBeanModule.newExporter(binder).export(PersonStore.class).withGeneratedName();

        binder.bind(PersonsResource.class).in(Scopes.SINGLETON);
        binder.bind(PersonResource.class).in(Scopes.SINGLETON);

        ConfigurationModule.bindConfig(binder).to(StoreConfig.class);
    }
}
