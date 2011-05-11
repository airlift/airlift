package com.proofpoint.event.client;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.testng.annotations.BeforeMethod;

public class TestNullEventModule extends AbstractTestNullEventClient
{
    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new NullEventModule()
        );
        eventClient = (NullEventClient) injector.getInstance(EventClient.class);
    }
}
