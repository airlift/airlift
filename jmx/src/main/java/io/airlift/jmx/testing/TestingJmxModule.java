package io.airlift.jmx.testing;

import com.google.inject.Binder;
import com.google.inject.Module;
import javax.management.MBeanServer;
import org.weakref.jmx.testing.TestingMBeanServer;

public class TestingJmxModule implements Module {
    @Override
    public void configure(Binder binder) {
        binder.disableCircularProxies();

        binder.bind(MBeanServer.class).toInstance(new TestingMBeanServer());
    }
}
