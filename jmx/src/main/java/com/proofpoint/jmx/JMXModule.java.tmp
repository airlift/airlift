package com.proofpoint.jmx;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import org.weakref.jmx.guice.ExportBuilder;
import org.weakref.jmx.guice.MBeanModule;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;

public class JmxModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(MBeanServer.class).toInstance(ManagementFactory.getPlatformMBeanServer());
        binder.bind(JmxAgent.class).in(Scopes.SINGLETON);
        ConfigurationModule.bindConfig(binder).to(JmxConfig.class);

        ExportBuilder builder = MBeanModule.newExporter(binder);
        builder.export(StackTraceMBean.class).withGeneratedName();
    }
}

