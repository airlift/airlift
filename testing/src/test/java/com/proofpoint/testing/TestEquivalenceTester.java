package com.proofpoint.testing;

import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.fail;

public class TestEquivalenceTester
{
    @Test
    public void testCheck()
    {
        EquivalenceTester.check(asList("a", "a", "a"),
                                asList("b", "b", "b"));
    }

    @Test
    public void testDetects()
    {
        try {
            EquivalenceTester.check(asList("a", "a", "b"));
            fail();
        }
        catch (AssertionError e) {
            // success
        }
    }
}
