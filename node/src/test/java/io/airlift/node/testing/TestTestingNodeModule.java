package io.airlift.node.testing;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.node.NodeInfo;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertNotEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestTestingNodeModule
{
    @Test
    public void testTestingNode()
            throws UnknownHostException
    {
        long testStartTime = System.currentTimeMillis();

        Injector injector = Guice.createInjector(new TestingNodeModule());
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertNotNull(nodeInfo);
        assertTrue(nodeInfo.getEnvironment().matches("test\\d+"));
        assertEquals(nodeInfo.getPool(), "general");
        assertNotNull(nodeInfo.getNodeId());
        assertNotNull(nodeInfo.getLocation());
        assertNull(nodeInfo.getBinarySpec());
        assertNull(nodeInfo.getConfigSpec());
        assertNotNull(nodeInfo.getInstanceId());

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertEquals(nodeInfo.getInternalAddress(), "127.0.0.1");
        assertEquals(nodeInfo.getBindIp(), InetAddress.getByName(nodeInfo.getInternalAddress()));
        assertEquals(nodeInfo.getExternalAddress(), "127.0.0.1");

        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        assertNotNull(nodeInfo.toString());
    }

    @Test
    public void testTestingNodeExplicitEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule("foo"));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertNotNull(nodeInfo);
        assertEquals(nodeInfo.getEnvironment(), "foo");
    }

    @Test
    public void testTestingNodePresentEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule(Optional.of("foo")));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertNotNull(nodeInfo);
        assertEquals(nodeInfo.getEnvironment(), "foo");
    }

    @Test
    public void testTestingNodeAbsentEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule(Optional.empty()));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertNotNull(nodeInfo);
        assertTrue(nodeInfo.getEnvironment().matches("test\\d+"));
    }
}
