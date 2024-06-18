package io.airlift.log;

import com.google.inject.Guice;
import com.google.inject.Stage;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

public class TestLogJmxModule
{
    @Test
    public void testModule()
            throws Exception
    {
        Guice.createInjector(Stage.PRODUCTION,
                new TestingMBeanModule(),
                new LogJmxModule());
    }
}
