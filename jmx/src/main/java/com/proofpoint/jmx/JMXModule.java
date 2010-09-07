package com.proofpoint.jmx;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationModule;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;

public class JMXModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.bind(MBeanServer.class).toInstance(ManagementFactory.getPlatformMBeanServer());
        ConfigurationModule.bindConfig(binder, JMXConfig.class);
    }
}

