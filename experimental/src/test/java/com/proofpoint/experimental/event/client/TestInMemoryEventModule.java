package com.proofpoint.experimental.event.client;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.testng.annotations.BeforeMethod;

public class TestInMemoryEventModule extends AbstractTestInMemoryEventClient
{
    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new InMemoryEventModule()
        );
        eventClient = injector.getInstance(InMemoryEventClient.class);
    }
}
