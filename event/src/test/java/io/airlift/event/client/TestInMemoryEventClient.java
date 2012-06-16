package io.airlift.event.client;

import org.testng.annotations.BeforeMethod;

public class TestInMemoryEventClient extends AbstractTestInMemoryEventClient
{

    @BeforeMethod
    public void setup()
            throws Exception
    {
        eventClient = new InMemoryEventClient();
    }

}
