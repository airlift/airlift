package io.airlift.http.server;

import org.testng.Assert;
import org.testng.annotations.Test;

import static io.airlift.http.server.Inet4Networks.isPrivateNetworkAddress;

public class TestInet4Networks
{
    @Test
    public void test()
    {
        Assert.assertTrue(isPrivateNetworkAddress("127.0.0.1"));
        Assert.assertTrue(isPrivateNetworkAddress("127.1.2.3"));
        Assert.assertTrue(isPrivateNetworkAddress("169.254.0.1"));
        Assert.assertTrue(isPrivateNetworkAddress("169.254.1.2"));
        Assert.assertTrue(isPrivateNetworkAddress("192.168.0.1"));
        Assert.assertTrue(isPrivateNetworkAddress("192.168.1.2"));
        Assert.assertTrue(isPrivateNetworkAddress("172.16.0.1"));
        Assert.assertTrue(isPrivateNetworkAddress("172.16.1.2"));
        Assert.assertTrue(isPrivateNetworkAddress("172.16.1.2"));
        Assert.assertTrue(isPrivateNetworkAddress("10.0.0.1"));
        Assert.assertTrue(isPrivateNetworkAddress("10.1.2.3"));

        Assert.assertFalse(isPrivateNetworkAddress("1.2.3.4"));
        Assert.assertFalse(isPrivateNetworkAddress("172.33.0.0"));
    }
}
