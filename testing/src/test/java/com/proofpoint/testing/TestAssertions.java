package com.proofpoint.testing;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAssertions
{
    @Test
    public void testAssertEqualsIgnoreCase()
    {
        Assertions.assertEqualsIgnoreCase("hello", "hello");
        Assertions.assertEqualsIgnoreCase("hello", "Hello");
        Assertions.assertEqualsIgnoreCase("hello", "HELLO");

        try {
            Assertions.assertEqualsIgnoreCase("hello", "bye");
            Assert.fail();
        }
        catch (Throwable e) {
            // success
        }
    }

}
