package com.proofpoint.jmx;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;

public class JMXModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(MBeanServer.class).toInstance(ManagementFactory.getPlatformMBeanServer());
        binder.bind(JMXAgent.class).in(Scopes.SINGLETON);
        ConfigurationModule.bindConfig(binder, JMXConfig.class);
    }
}

