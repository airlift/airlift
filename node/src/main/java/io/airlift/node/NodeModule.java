package com.proofpoint.node;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import org.weakref.jmx.guice.MBeanModule;

public class NodeModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(NodeInfo.class).in(Scopes.SINGLETON);
        ConfigurationModule.bindConfig(binder).to(NodeConfig.class);
        MBeanModule.newExporter(binder).export(NodeInfo.class).withGeneratedName();
    }
}
