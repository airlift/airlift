package com.proofpoint.experimental.discovery.client;

import org.testng.annotations.Test;

import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

public class TestServiceSelectorProvider
{
    @Test
    public void testEquivalence()
    {
        ServiceSelectorProvider fooWithClient = new ServiceSelectorProvider("foo");

        equivalenceTester()
                .addEquivalentGroup(new ServiceSelectorProvider("foo"), fooWithClient)
                .addEquivalentGroup(new ServiceSelectorProvider("bar"), new ServiceSelectorProvider("bar"))
                .check();
    }
}
