package io.airlift.log;

import com.google.common.annotations.Beta;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import org.weakref.jmx.guice.MBeanModule;

@Beta
public class LogJmxModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(LoggingMBean.class).in(Scopes.SINGLETON);
        MBeanModule.newExporter(binder).export(LoggingMBean.class).as("io.airlift.log:name=Logging");
    }
}
