package com.proofpoint.testing;

import static java.lang.String.format;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class Assertions
{
    public static void assertEqualsIgnoreCase(String actual, String expected)
    {
        assertNotNull(actual);
        assertTrue(actual.equalsIgnoreCase(expected), 
                   format("expected:<" + expected + "> but was:<" + actual + ">", expected, actual));

    }

}
