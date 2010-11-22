package com.proofpoint.testing;

import static org.testng.Assert.assertNotNull;

import static java.lang.String.format;

public final class Assertions
{
    private Assertions()
    {
    }

    public static void assertEqualsIgnoreCase(String actual, String expected) {
        assertEqualsIgnoreCase(actual, expected, null);
    }

    public static void assertEqualsIgnoreCase(String actual, String expected, String message)
    {
        assertNotNull(actual, "actual is null");
        if (actual.equalsIgnoreCase(expected)) {
            // ok
            return;
        }
        throw new AssertionError(format("%sexpected:<%s> to equal ignoring case <%s>", toMessageString(message), actual, expected));
    }

    public static <T extends Comparable<T>> void assertGreaterThan(T actual, T expected) {
        assertGreaterThan(actual, expected, null);
    }

    public static <T extends Comparable<T>> void assertGreaterThan(T actual, T expected, String message) {
        assertNotNull(actual, "actual is null");
        try {
            if (actual.compareTo(expected) > 0) {
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            String foo = format("%sexpected:<%s> to be greater than <%s>, but %s is not comparable %s", toMessageString(message), actual, expected, actual.getClass().getName(), expected.getClass().getName());
            AssertionError error = new AssertionError(foo);
            error.initCause(e);
            throw error;
        }
        String foo = format("%sexpected:<%s> to be greater than <%s>", toMessageString(message), actual, expected);
        throw new AssertionError(foo);
    }

    public static <T extends Comparable<T>> void assertLessThan(T actual, T expected) {
        assertLessThan(actual, expected, null);
    }

    public static <T extends Comparable<T>> void assertLessThan(T actual, T expected, String message) {
        assertNotNull(actual, "actual is null");
        try {
            if (actual.compareTo(expected) < 0) {
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            String foo = format("%sexpected:<%s> to be less than <%s>, but %s is not comparable %s", toMessageString(message), actual, expected, actual.getClass().getName(), expected.getClass().getName());
            AssertionError error = new AssertionError(foo);
            error.initCause(e);
            throw error;
        }
        throw new AssertionError(format("%sexpected:<%s> to be less than <%s>", toMessageString(message), actual, expected));
    }

    public static <T extends Comparable<T>> void assertBetweenInclusive(T actual, T lowerBound, T upperBound) {
        assertBetweenInclusive(actual, lowerBound, upperBound, null);
    }

    public static <T extends Comparable<T>> void assertBetweenInclusive(T actual, T lowerBound, T upperBound, String message) {
        assertNotNull(actual, "actual is null");
        try {
            if (actual.compareTo(lowerBound) >= 0 && actual.compareTo(upperBound) <= 0) {
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            String foo = format("%sexpected:<%s> to be between <%s> and <%s> inclusive, but %s is not comparable with %s or %s",
                    toMessageString(message),
                    actual,
                    lowerBound,
                    upperBound,
                    actual.getClass().getName(),
                    lowerBound.getClass().getName(), 
                    upperBound.getClass().getName());
            AssertionError error = new AssertionError(foo);
            error.initCause(e);
            throw error;
        }
        throw new AssertionError(format("%sexpected:<%s> to be between <%s> and <%s> inclusive", toMessageString(message), actual, lowerBound, upperBound));
    }

    public static <T extends Comparable<T>> void assertBetweenExclusive(T actual, T lowerBound, T upperBound) {
        assertBetweenExclusive(actual, lowerBound, upperBound, null);
    }

    public static <T extends Comparable<T>> void assertBetweenExclusive(T actual, T lowerBound, T upperBound, String message) {
        assertNotNull(actual, "actual is null");
        try {
            if (actual.compareTo(lowerBound) > 0 && actual.compareTo(upperBound) < 0) {
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            String foo = format("%sexpected:<%s> to be between <%s> and <%s> exclusive, but %s is not comparable with %s or %s",
                    toMessageString(message),
                    actual,
                    lowerBound,
                    upperBound,
                    actual.getClass().getName(),
                    lowerBound.getClass().getName(),
                    upperBound.getClass().getName());
            AssertionError error = new AssertionError(foo);
            error.initCause(e);
            throw error;
        }
        throw new AssertionError(format("%sexpected:<%s> to be between <%s> and <%s> exclusive", toMessageString(message), actual, lowerBound, upperBound));
    }

    public static void assertInstanceof(Object actual, Class<?> expectedType)
    {
        assertInstanceof(actual, expectedType, null);
    }

    public static void assertInstanceof(Object actual, Class<?> expectedType, String message)
    {
        assertNotNull(actual, "actual is null");
        assertNotNull(expectedType, "expectedType is null");
        if (expectedType.isInstance(actual)) {
            // ok
            return;
        }
        throw new AssertionError(format("%sexpected:<%s> to be an instance of <%s>", toMessageString(message), actual, expectedType.getName()));
    }

    private static String toMessageString(String message)
    {
        return message == null ? "" : message + " ";
    }
}
