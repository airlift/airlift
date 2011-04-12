package com.proofpoint.node.testing;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import org.weakref.jmx.guice.MBeanModule;

import java.util.Random;

public class TestingNodeModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(NodeInfo.class).in(Scopes.SINGLETON);
        binder.bind(NodeConfig.class).toInstance(new NodeConfig().setEnvironment("test" + new Random().nextInt(1000000)));
        MBeanModule.newExporter(binder).export(NodeInfo.class).withGeneratedName();
    }
}
