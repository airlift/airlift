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
import static org.assertj.core.api.Assertions.assertThat;

public class TestTestingNodeModule
{
    @Test
    public void testTestingNode()
            throws UnknownHostException
    {
        long testStartTime = System.currentTimeMillis();

        Injector injector = Guice.createInjector(new TestingNodeModule());
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertThat(nodeInfo).isNotNull();
        assertThat(nodeInfo.getEnvironment()).matches("test\\d+");
        assertThat(nodeInfo.getPool()).isEqualTo("general");
        assertThat(nodeInfo.getNodeId()).isNotNull();
        assertThat(nodeInfo.getLocation()).isNotNull();
        assertThat(nodeInfo.getBinarySpec()).isNull();
        assertThat(nodeInfo.getConfigSpec()).isNull();
        assertThat(nodeInfo.getInstanceId()).isNotNull();

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertThat(nodeInfo.getInternalAddress()).isEqualTo("127.0.0.1");
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddress.getByName(nodeInfo.getInternalAddress()));
        assertThat(nodeInfo.getExternalAddress()).isEqualTo("127.0.0.1");

        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        assertThat(nodeInfo.toString()).isNotNull();
    }

    @Test
    public void testTestingNodeExplicitEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule("foo"));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertThat(nodeInfo).isNotNull();
        assertThat(nodeInfo.getEnvironment()).isEqualTo("foo");
    }

    @Test
    public void testTestingNodePresentEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule(Optional.of("foo")));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertThat(nodeInfo).isNotNull();
        assertThat(nodeInfo.getEnvironment()).isEqualTo("foo");
    }

    @Test
    public void testTestingNodeAbsentEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule(Optional.empty()));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertThat(nodeInfo).isNotNull();
        assertThat(nodeInfo.getEnvironment()).matches("test\\d+");
    }
}
