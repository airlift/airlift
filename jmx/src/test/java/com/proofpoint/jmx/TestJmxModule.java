package com.proofpoint.jmx;

import com.beust.jcommander.internal.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import org.testng.annotations.Test;

import java.util.Map;

public class TestJmxModule
{
    @Test
    public void testCanConstruct()
    {
        Map<String, String> properties = Maps.newHashMap();
        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new JmxModule(), new ConfigurationModule(configFactory));
        injector.getInstance(JmxAgent.class);
    }
}
