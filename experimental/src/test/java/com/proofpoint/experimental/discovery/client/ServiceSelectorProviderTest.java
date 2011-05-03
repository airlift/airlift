package com.proofpoint.experimental.discovery.client;

import com.proofpoint.node.NodeInfo;
import org.testng.annotations.Test;

import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

public class ServiceSelectorProviderTest
{
    @Test
    public void testEquivalence()
    {

        ServiceSelectorProvider fooWithClient = new ServiceSelectorProvider("foo");
        fooWithClient.setClient(new InMemoryDiscoveryClient(new NodeInfo("test")));

        equivalenceTester()
                .addEquivalentGroup(new ServiceSelectorProvider("foo"), fooWithClient)
                .addEquivalentGroup(new ServiceSelectorProvider("bar"), new ServiceSelectorProvider("bar"))
                .check();
    }
}
