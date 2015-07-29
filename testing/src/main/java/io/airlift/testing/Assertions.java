/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.testing;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMultiset;
import org.testng.Assert;

import static org.testng.Assert.assertNotNull;

public final class Assertions
{
    private Assertions()
    {
    }

    public static void assertContains(String actual, String expectedPart)
    {
        assertContains(actual, expectedPart, null);
    }

    public static void assertContainsAllOf(String actual, String... expectedParts)
    {
        // todo improve naive implementation
        for (String expected : expectedParts) {
            assertContains(actual, expected, null);
        }
    }

    public static void assertContains(String actual, String expectedPart, String message)
    {
        assertNotNull(actual, "actual is null");
        assertNotNull(expectedPart, "expectedPart is null");
        if (actual.contains(expectedPart)) {
            // ok
            return;
        }
        fail("%sexpected:<%s> to contain <%s>", toMessageString(message), actual, expectedPart);
    }

    public static void assertEqualsIgnoreCase(String actual, String expected)
    {
        assertEqualsIgnoreCase(actual, expected, null);
    }

    public static void assertEqualsIgnoreCase(String actual, String expected, String message)
    {
        assertNotNull(actual, "actual is null");
        if (actual.equalsIgnoreCase(expected)) {
            // ok
            return;
        }
        fail("%sexpected:<%s> to equal ignoring case <%s>", toMessageString(message), actual, expected);
    }

    public static void assertNotEquals(Object actual, Object expected)
    {
        assertNotEquals(actual, expected, null);
    }

    public static void assertNotEquals(Object actual, Object expected, String message)
    {
        if (actual == null) {
            if (expected != null) {
                // ok
                return;
            }
        }
        else {
            if (!actual.equals(expected)) {
                // ok
                return;
            }
        }
        fail("%sexpected:<%s> to not equal <%s>", toMessageString(message), actual, expected);
    }

    public static <T extends Comparable<T>> void assertGreaterThan(T actual, T expected)
    {
        assertGreaterThan(actual, expected, null);
    }

    public static <T extends Comparable<T>> void assertGreaterThan(T actual, T expected, String message)
    {
        assertNotNull(actual, "actual is null");
        try {
            if (actual.compareTo(expected) > 0) {
                if (!(expected.compareTo(actual) < 0)) {
                    fail("%scomparison symmetry: <%s> is greater than <%s>, but <%s> is not less than <%s>",
                            toMessageString(message),
                            actual,
                            expected,
                            expected,
                            actual);
                }
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            fail(e, "%sexpected:<%s> to be greater than <%s>, but %s is not comparable %s",
                    toMessageString(message),
                    actual,
                    expected,
                    actual.getClass().getName(),
                    expected.getClass().getName());
        }
        fail("%sexpected:<%s> to be greater than <%s>", toMessageString(message), actual, expected);
    }

    public static <T extends Comparable<T>> void assertGreaterThanOrEqual(T actual, T expected)
    {
        assertGreaterThanOrEqual(actual, expected, null);
    }

    public static <T extends Comparable<T>> void assertGreaterThanOrEqual(T actual, T expected, String message)
    {
        assertNotNull(actual, "actual is null");
        try {
            int compareValue = actual.compareTo(expected);
            if (compareValue >= 0) {
                int reverseCompareValue = expected.compareTo(actual);
                if (!(reverseCompareValue <= 0 && (compareValue != 0 || reverseCompareValue == 0))) {
                    fail("%scomparison symmetry: <%s> is greater than or equal to <%s>, but <%s> is not less than or equal to<%s>",
                            toMessageString(message),
                            actual,
                            expected,
                            expected,
                            actual);
                }
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            fail(e, "%sexpected:<%s> to be greater than or equal to <%s>, but %s is not comparable %s",
                    toMessageString(message),
                    actual,
                    expected,
                    actual.getClass().getName(),
                    expected.getClass().getName());
        }

        fail("%sexpected:<%s> to be greater than or equal to <%s>", toMessageString(message), actual, expected);
    }

    public static <T extends Comparable<T>> void assertLessThan(T actual, T expected)
    {
        assertLessThan(actual, expected, null);
    }

    public static <T extends Comparable<T>> void assertLessThan(T actual, T expected, String message)
    {
        assertNotNull(actual, "actual is null");
        try {
            if (actual.compareTo(expected) < 0) {
                if (!(expected.compareTo(actual) > 0)) {
                    fail("%scomparison symmetry: <%s> is less than <%s>, but <%s> is not greater than <%s>",
                            toMessageString(message),
                            actual,
                            expected,
                            expected,
                            actual);
                }
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            fail(e, "%sexpected:<%s> to be less than <%s>, but %s is not comparable %s",
                    toMessageString(message),
                    actual,
                    expected,
                    actual.getClass().getName(),
                    expected.getClass().getName());
        }
        fail("%sexpected:<%s> to be less than <%s>", toMessageString(message), actual, expected);
    }

    public static <T extends Comparable<T>> void assertLessThanOrEqual(T actual, T expected)
    {
        assertLessThanOrEqual(actual, expected, null);
    }

    public static <T extends Comparable<T>> void assertLessThanOrEqual(T actual, T expected, String message)
    {
        assertNotNull(actual, "actual is null");
        try {
            int compareValue = actual.compareTo(expected);
            if (compareValue <= 0) {
                int reverseCompareValue = expected.compareTo(actual);
                if (!(reverseCompareValue >= 0 && (compareValue != 0 || reverseCompareValue == 0))) {
                    fail("%scomparison symmetry: <%s> is less than or equal to <%s>, but <%s> is not greater than or equal to <%s>",
                            toMessageString(message),
                            actual,
                            expected,
                            expected,
                            actual);
                }
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            fail(e, "%sexpected:<%s> to be less than or equal to <%s>, but %s is not comparable %s",
                    toMessageString(message),
                    actual,
                    expected,
                    actual.getClass().getName(),
                    expected.getClass().getName());
        }
        fail("%sexpected:<%s> to be less than or equal to <%s>", toMessageString(message), actual, expected);
    }

    public static <T extends Comparable<T>> void assertBetweenInclusive(T actual, T lowerBound, T upperBound)
    {
        assertBetweenInclusive(actual, lowerBound, upperBound, null);
    }

    public static <T extends Comparable<T>> void assertBetweenInclusive(T actual, T lowerBound, T upperBound, String message)
    {
        assertNotNull(actual, "actual is null");
        try {
            if (actual.compareTo(lowerBound) >= 0 && actual.compareTo(upperBound) <= 0) {
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            fail(e, "%sexpected:<%s> to be between <%s> and <%s> inclusive, but %s is not comparable with %s or %s",
                    toMessageString(message),
                    actual,
                    lowerBound,
                    upperBound,
                    actual.getClass().getName(),
                    lowerBound.getClass().getName(),
                    upperBound.getClass().getName());
        }
        fail("%sexpected:<%s> to be between <%s> and <%s> inclusive", toMessageString(message), actual, lowerBound, upperBound);
    }

    public static <T extends Comparable<T>> void assertBetweenExclusive(T actual, T lowerBound, T upperBound)
    {
        assertBetweenExclusive(actual, lowerBound, upperBound, null);
    }

    public static <T extends Comparable<T>> void assertBetweenExclusive(T actual, T lowerBound, T upperBound, String message)
    {
        assertNotNull(actual, "actual is null");
        try {
            if (actual.compareTo(lowerBound) > 0 && actual.compareTo(upperBound) < 0) {
                // ok
                return;
            }
        }
        catch (ClassCastException e) {
            fail(e, "%sexpected:<%s> to be between <%s> and <%s> exclusive, but %s is not comparable with %s or %s",
                    toMessageString(message),
                    actual,
                    lowerBound,
                    upperBound,
                    actual.getClass().getName(),
                    lowerBound.getClass().getName(),
                    upperBound.getClass().getName());
        }
        fail("%sexpected:<%s> to be between <%s> and <%s> exclusive", toMessageString(message), actual, lowerBound, upperBound);
    }

    public static <T, U extends T> void assertInstanceOf(T actual, Class<U> expectedType)
    {
        assertInstanceOf(actual, expectedType, null);
    }

    public static <T, U extends T> void assertInstanceOf(T actual, Class<U> expectedType, String message)
    {
        assertNotNull(actual, "actual is null");
        assertNotNull(expectedType, "expectedType is null");
        if (expectedType.isInstance(actual)) {
            // ok
            return;
        }
        fail("%sexpected:<%s> to be an instance of <%s>", toMessageString(message), actual, expectedType.getName());
    }

    public static void assertEqualsIgnoreOrder(Iterable<?> actual, Iterable<?> expected)
    {
        assertEqualsIgnoreOrder(actual, expected, null);
    }

    public static void assertEqualsIgnoreOrder(Iterable<?> actual, Iterable<?> expected, String message)
    {
        assertNotNull(actual, "actual is null");
        assertNotNull(expected, "expected is null");


        ImmutableMultiset<?> actualSet = ImmutableMultiset.copyOf(actual);
        ImmutableMultiset<?> expectedSet = ImmutableMultiset.copyOf(expected);
        if (!actualSet.equals(expectedSet)) {
            Joiner joiner = Joiner.on("\n      ");
            fail("%sexpected: collections to be equal (ignoring order).%nActual:%n      %s%nExpected:%n      %s", toMessageString(message), joiner.join(actual), joiner.join(expected));
        }
    }

    private static String toMessageString(String message)
    {
        return message == null ? "" : message + " ";
    }

    private static void fail(String format, Object... args)
    {
        String message = String.format(format, args);
        Assert.fail(message);

    }

    private static void fail(Throwable e, String format, Object... args)
    {
        String message = String.format(format, args);
        Assert.fail(message, e);
    }
}
