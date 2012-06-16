package io.airlift.tracetoken;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestTraceTokenManager
{
    @Test
    public void testNoToken()
    {
        TraceTokenManager manager = new TraceTokenManager();
        assertNull(manager.getCurrentRequestToken());
    }

    @Test
    public void testCreateToken()
    {
        TraceTokenManager manager = new TraceTokenManager();

        String token = manager.createAndRegisterNewRequestToken();
        assertEquals(manager.getCurrentRequestToken(), token);
    }

    @Test
    public void testRegisterCustomToken()
    {
        TraceTokenManager manager = new TraceTokenManager();
        manager.registerRequestToken("abc");

        assertEquals(manager.getCurrentRequestToken(), "abc");
    }

    @Test
    public void testOverrideRequestToken()
    {
        TraceTokenManager manager = new TraceTokenManager();
        String oldToken = manager.createAndRegisterNewRequestToken();

        assertEquals(manager.getCurrentRequestToken(), oldToken);

        manager.registerRequestToken("abc");
        assertEquals(manager.getCurrentRequestToken(), "abc");
    }
}
