package io.airlift.event.client;

import org.junit.jupiter.api.BeforeEach;

public class TestMultiEventClient
        extends AbstractTestMultiEventClient
{
    @BeforeEach
    public void setUp()
    {
        memoryEventClient1 = new InMemoryEventClient();
        memoryEventClient2 = new InMemoryEventClient();
        eventClient = new MultiEventClient(memoryEventClient1, new NullEventClient(), memoryEventClient2, new NullEventClient());
    }
}
