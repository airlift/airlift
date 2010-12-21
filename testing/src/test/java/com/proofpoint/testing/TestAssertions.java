package com.proofpoint.testing;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.Serializable;

import static com.proofpoint.testing.TestAssertions.SubComparable.createSubComparable;
import static com.proofpoint.testing.TestAssertions.SuperComparable.createSuperComparable;

public class TestAssertions
{
    private static final String MESSAGE = "@message@";

    @Test
    public void testAssertContains()
    {
        passContains("hello", "hello");
        passContains("XXX hello XXXX", "hello");

        failContains("hello", "bye");
        failContains("XXX hello XXX", "HELLO");
    }

    private void passContains(String actual, String expected)
    {
        Assertions.assertContains(actual, expected);
        Assertions.assertContains(actual, expected, MESSAGE);
    }

    private void failContains(String actual, String expected)
    {
        try {
            Assertions.assertContains(actual, expected);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }

        try {
            Assertions.assertContains(actual, expected, MESSAGE);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            // success
            verifyExceptionMessage(e, MESSAGE, actual, expected);
        }
    }

    @Test
    public void testAssertEqualsIgnoreCase()
    {
        passEqualsIgnoreCase("hello", "hello");
        passEqualsIgnoreCase("hello", "Hello");
        passEqualsIgnoreCase("hello", "HeLlO");
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
        passGreaterThan(createSuperComparable("bob"), createSuperComparable("alice"));

        failGreaterThan(1, 2);
        failGreaterThan(1, 1);
        failGreaterThan("alice", "bob");
        failGreaterThan("alice", "alice");

        // not comparable
        failGreaterThan("bob", 1);

        // invalid comparison
        failGreaterThan(createSuperComparable("bob"), createSubComparable("alice"));
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
    public void testAssertGreaterThanOrEqual()
    {
        passGreaterThanOrEqual(2, 1);
        passGreaterThanOrEqual(2, 2);
        passGreaterThanOrEqual("bob", "alice");
        passGreaterThanOrEqual("bob", "bob");
        passGreaterThanOrEqual(createSuperComparable("bob"), createSuperComparable("alice"));
        passGreaterThanOrEqual(createSuperComparable("bob"), createSuperComparable("bob"));

        failGreaterThanOrEqual(1, 2);
        failGreaterThanOrEqual("alice", "bob");

        // not comparable
        failGreaterThanOrEqual("bob", 1);

        // invalid comparison
        failGreaterThanOrEqual(createSuperComparable("bob"), createSubComparable("alice"));
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void passGreaterThanOrEqual(Comparable actual, Comparable expected)
    {
        Assertions.assertGreaterThanOrEqual(actual, expected);
        Assertions.assertGreaterThanOrEqual(actual, expected, MESSAGE);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void failGreaterThanOrEqual(Comparable actual, Comparable expected)
    {
        try {
            Assertions.assertGreaterThanOrEqual(actual, expected);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }
        try {
            Assertions.assertGreaterThanOrEqual(actual, expected, MESSAGE);
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
        passLessThan(createSuperComparable("alice"), createSuperComparable("bob"));

        failLessThan(2, 1);
        failLessThan(2, 2);
        failLessThan("bob", "alice");
        failLessThan("bob", "bob");

        // not comparable
        failLessThan("bob", 1);

        // invalid comparison
        failLessThan(createSuperComparable("alice"), createSubComparable("bob"));
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
    public void testAssertLessThanOrEqual()
    {
        passLessThanOrEqual(1, 2);
        passLessThanOrEqual(1, 1);
        passLessThanOrEqual("alice", "bob");
        passLessThanOrEqual("alice", "alice");
        passLessThanOrEqual(createSuperComparable("alice"), createSuperComparable("bob"));
        passLessThanOrEqual(createSuperComparable("alice"), createSuperComparable("alice"));

        failLessThanOrEqual(2, 1);
        failLessThanOrEqual("bob", "alice");

        // not comparable
        failLessThanOrEqual("bob", 1);

        // invalid comparison
        failLessThanOrEqual(createSuperComparable("alice"), createSubComparable("bob"));
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void passLessThanOrEqual(Comparable actual, Comparable expected)
    {
        Assertions.assertLessThanOrEqual(actual, expected);
        Assertions.assertLessThanOrEqual(actual, expected, MESSAGE);
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public void failLessThanOrEqual(Comparable actual, Comparable expected)
    {
        try {
            Assertions.assertLessThanOrEqual(actual, expected);
            Assert.fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }
        try {
            Assertions.assertLessThanOrEqual(actual, expected, MESSAGE);
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

    public static class SuperComparable<T extends Comparable<T>> implements Comparable<SuperComparable<T>>
    {

        public static <T extends Comparable<T>> SuperComparable<T> createSuperComparable(T value)
        {
            return new SuperComparable<T>(value);
        }

        protected final T value;

        private SuperComparable(T value)
        {
            this.value = value;
        }

        @Override
        public int compareTo(SuperComparable<T> other)
        {
            return value.compareTo(other.value);
        }

        @Override
        public String toString()
        {
            return "SuperComparable{" + value + '}';
        }
    }

    public static class SubComparable<T extends Comparable<T>> extends SuperComparable<T>
    {
        public static <T extends Comparable<T>> SubComparable<T> createSubComparable(T value)
        {
            return new SubComparable<T>(value);
        }

        private SubComparable(T value)
        {
            super(value);
        }

        @Override
        public int compareTo(SuperComparable<T> other)
        {
            int value = super.compareTo(other);
            if (value == 0) {
                return 42;
            }
            return -value;
        }


        @Override
        public String toString()
        {
            return "SubComparable{" + value + '}';
        }
    }
}
