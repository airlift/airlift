package io.airlift.jmx.testing;

import com.google.inject.Binder;
import com.google.inject.Module;
import org.weakref.jmx.testing.TestingMBeanServer;

import javax.management.MBeanServer;

public class TestingJmxModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(MBeanServer.class).toInstance(new TestingMBeanServer());
    }
}
