package com.proofpoint.configuration;

import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class DeprecatedConfigAnnotationTest
{
    @Test
    public void testNotAnnotated()
            throws NoSuchMethodException
    {
        assertFalse(this.getClass().getMethod("testNotAnnotated").isAnnotationPresent(DeprecatedConfig.class));
    }

    @Test
    @DeprecatedConfig({"one"})
    public void testSingleEntry()
            throws NoSuchMethodException
    {
        Method self = this.getClass().getMethod("testSingleEntry");
        assertTrue(self.isAnnotationPresent(DeprecatedConfig.class));

        DeprecatedConfig annotation = self.getAnnotation(DeprecatedConfig.class);

        assertEquals(annotation.value().length, 1);
        assertEquals(annotation.value()[0], "one");

    }

    @Test
    @DeprecatedConfig({"one","two"})
    public void testMultipleEntries()
            throws NoSuchMethodException
    {
        Method self = this.getClass().getMethod("testMultipleEntries");
        assertTrue(self.isAnnotationPresent(DeprecatedConfig.class));

        DeprecatedConfig annotation = self.getAnnotation(DeprecatedConfig.class);

        assertEquals(annotation.value().length, 2);
        assertEquals(annotation.value()[0], "one");
        assertEquals(annotation.value()[1], "two");
    }
}
