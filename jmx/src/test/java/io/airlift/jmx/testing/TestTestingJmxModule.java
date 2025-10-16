package io.airlift.jmx.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import javax.management.MBeanServer;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.testing.TestingMBeanServer;

public class TestTestingJmxModule {
    @Test
    public void testTestingJmxModule() {
        Injector injector = Guice.createInjector(new TestingJmxModule());
        MBeanServer server = injector.getInstance(MBeanServer.class);

        assertThat(server).isInstanceOf(TestingMBeanServer.class);
    }
}
