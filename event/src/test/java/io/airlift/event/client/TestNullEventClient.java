package io.airlift.event.client;

import org.testng.annotations.BeforeMethod;

public class TestNullEventClient extends AbstractTestNullEventClient
{
    @BeforeMethod
    public void setUp()
            throws Exception
    {
        eventClient = new NullEventClient();
    }
}
