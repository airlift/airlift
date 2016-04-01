package io.airlift.http.client;

import org.testng.annotations.Test;

import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static org.testng.Assert.assertEquals;

public class TestHeaderName
{
    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(HeaderName.of("foo"), HeaderName.of("FOO"), HeaderName.of("foo"))
                .addEquivalentGroup(HeaderName.of("bar"), HeaderName.of("BAR"), HeaderName.of("bar"))
                .check();
    }

    @Test
    public void testToString()
    {
        assertEquals(HeaderName.of("FOO").toString(), "FOO");
        assertEquals(HeaderName.of("foo").toString(), "foo");
    }
}
