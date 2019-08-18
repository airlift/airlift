package io.airlift.log;

import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class TestHostname
{
    @Test
    public void testHostname()
            throws UnknownHostException
    {
        System.out.println("LOCALHOST: " + InetAddress.getLocalHost().getCanonicalHostName());
        throw new AssertionError();
    }
}
