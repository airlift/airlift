package io.airlift.event.client;

import org.testng.annotations.BeforeMethod;

public class TestMultiEventClient
        extends AbstractTestMultiEventClient
{
    @BeforeMethod
    public void setUp()
            throws Exception
    {
        memoryEventClient1 = new InMemoryEventClient();
        memoryEventClient2 = new InMemoryEventClient();
        eventClient = new MultiEventClient(memoryEventClient1, new NullEventClient(), memoryEventClient2, new NullEventClient());
    }
}
