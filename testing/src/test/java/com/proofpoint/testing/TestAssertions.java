package com.proofpoint.testing;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.Serializable;

public class TestAssertions
{
    private static final String MESSAGE = "@message@";

    @Test
    public void testAssertEqualsIgnoreCase()
    {
        passEqualsIgnoreCase("hello", "hello");
        passEqualsIgnoreCase("hello", "hello");
        passEqualsIgnoreCase("hello", "Hello");
        passEqualsIgnoreCase("hello", "HELLO");

        failEqualsIgnoreCase("hello", "bye");
    }

    private void passEqualsIgnoreCase(String actual, String expected)
    {
        Assertions.assertEqualsIgnoreCase(actual, expected);
        Assertions.assertEqualsIgnoreCase(expected, actual);
        Assertions.assertEqualsIgnoreCase(actual, expected, MESSAGE);
        Assertions.assertEqualsIgnoreCase(expected, actual, MESSAGE);
    }

    private void failEqualsIgnoreCase(String actual, String expected)
    {
        try {
            Assertions.assertEqualsIgnoreCase(actual, expected);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }

        try {
            Assertions.assertEqualsIgnoreCase(actual, expected, MESSAGE);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            // success
            verifyExceptionMessage(e, MESSAGE, actual, expected);
        }
    }

    @Test
    public void testAssertInstanceof()
    {
        passInstanceof("hello", Object.class);
        passInstanceof("hello", String.class);
        passInstanceof("hello", Serializable.class);
        passInstanceof("hello", CharSequence.class);
        passInstanceof("hello", Comparable.class);
        passInstanceof(42, Integer.class);

        failInstanceof("hello", Integer.class);
    }

    private void passInstanceof(Object actual, Class<?> expectedType)
    {
        Assertions.assertInstanceof(actual, expectedType);
        Assertions.assertInstanceof(actual, expectedType, MESSAGE);
    }

    private void failInstanceof(Object actual, Class<?> expectedType)
    {
        try {
            Assertions.assertInstanceof(actual, expectedType);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expectedType.getName());
        }

        try {
            Assertions.assertInstanceof(actual, expectedType, MESSAGE);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            // success
            verifyExceptionMessage(e, MESSAGE, actual, expectedType.getName());
        }
    }

    @Test
    public void testAssertGreaterThan()
    {
        passGreaterThan(2, 1);
        passGreaterThan("bob", "alice");
        failGreaterThan(1, 2);
        failGreaterThan("alice", "bob");
        failGreaterThan("bob", 1);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void passGreaterThan(Comparable actual, Comparable expected)
    {
        Assertions.assertGreaterThan(actual, expected);
        Assertions.assertGreaterThan(actual, expected, MESSAGE);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void failGreaterThan(Comparable actual, Comparable expected)
    {
        try {
            Assertions.assertGreaterThan(actual, expected);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }
        try {
            Assertions.assertGreaterThan(actual, expected, MESSAGE);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, MESSAGE, actual, expected);
        }
    }

    @Test
    public void testAssertLessThan()
    {
        passLessThan(1, 2);
        passLessThan("alice", "bob");
        failLessThan(2, 1);
        failLessThan("bob", "alice");
        failLessThan("bob", 1);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void passLessThan(Comparable actual, Comparable expected)
    {
        Assertions.assertLessThan(actual, expected);
        Assertions.assertLessThan(actual, expected, MESSAGE);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void failLessThan(Comparable actual, Comparable expected)
    {
        try {
            Assertions.assertLessThan(actual, expected);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }
        try {
            Assertions.assertLessThan(actual, expected, MESSAGE);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, MESSAGE, actual, expected);
        }
    }

    @Test
    public void testAssertBetweenInclusive()
    {
        passBetweenInclusive(1, 0, 2);
        passBetweenInclusive(1, 1, 2);
        passBetweenInclusive(1, 0, 1);
        passBetweenInclusive(1, 1, 1);
        passBetweenInclusive("bob", "alice", "charlie");
        passBetweenInclusive("bob", "bob", "charlie");
        passBetweenInclusive("bob", "alice", "bob");
        passBetweenInclusive("bob", "bob", "bob");
        failBetweenInclusive(1, 2, 3);
        failBetweenInclusive("alice", "bob", "charlie");
        failBetweenInclusive("bob", 1, 2);
        failBetweenInclusive("bob", 1, "alice");
        failBetweenInclusive("bob", "alice", 1);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void passBetweenInclusive(Comparable actual, Comparable lowerBound, Comparable upperBound)
    {
        Assertions.assertBetweenInclusive(actual, lowerBound, upperBound);
        Assertions.assertBetweenInclusive(actual, lowerBound, upperBound, MESSAGE);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void failBetweenInclusive(Comparable actual, Comparable lowerBound, Comparable upperBound)
    {
        try {
            Assertions.assertBetweenInclusive(actual, lowerBound, upperBound);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, lowerBound, upperBound);
        }
        try {
            Assertions.assertBetweenInclusive(actual, lowerBound, upperBound, MESSAGE);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, MESSAGE, actual, lowerBound, upperBound);
        }
    }
    @Test
    public void testAssertBetweenExclusive()
    {
        passBetweenExclusive(1, 0, 2);
        passBetweenExclusive("bob", "alice", "charlie");
        failBetweenExclusive(1, 2, 3);
        failBetweenExclusive(1, 1, 3);
        failBetweenExclusive(1, 0, 1);
        failBetweenExclusive("alice", "bob", "charlie");
        failBetweenExclusive("bob", "bob", "charlie");
        failBetweenExclusive("bob", "alice", "bob");
        failBetweenExclusive("bob", 1, 2);
        failBetweenExclusive("bob", 1, "alice");
        failBetweenExclusive("bob", "alice", 1);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void passBetweenExclusive(Comparable actual, Comparable lowerBound, Comparable upperBound)
    {
        Assertions.assertBetweenExclusive(actual, lowerBound, upperBound);
        Assertions.assertBetweenExclusive(actual, lowerBound, upperBound, MESSAGE);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void failBetweenExclusive(Comparable actual, Comparable lowerBound, Comparable upperBound)
    {
        try {
            Assertions.assertBetweenExclusive(actual, lowerBound, upperBound);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, lowerBound, upperBound);
        }
        try {
            Assertions.assertBetweenExclusive(actual, lowerBound, upperBound, MESSAGE);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, MESSAGE, actual, lowerBound, upperBound);
        }
    }

    private void verifyExceptionMessage(AssertionError e, String message, Object... values)
    {
        Assert.assertNotNull(e);
        String actualMessage = e.getMessage();
        Assert.assertNotNull(actualMessage);
        if (message != null) {
            Assert.assertTrue(actualMessage.startsWith(message + " "));
        }
        else {
            Assert.assertFalse(actualMessage.startsWith(" "));
        }

        for (Object value : values) {
            Assert.assertTrue(actualMessage.contains("<" + value + ">"));
        }
    }
}
