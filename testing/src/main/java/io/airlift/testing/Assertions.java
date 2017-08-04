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

import org.testng.Assert;

import static org.assertj.core.api.Assertions.assertThat;

public final class Assertions
{
    private Assertions()
    {
    }

    @Deprecated
    public static void assertContains(String actual, String expectedPart)
    {
        assertThat(actual).contains(expectedPart);
    }

    @Deprecated
    public static void assertContainsAllOf(String actual, String... expectedParts)
    {
        assertThat(actual).contains(expectedParts);
    }

    @Deprecated
    public static void assertContains(String actual, String expectedPart, String message)
    {
        assertThat(actual).contains(expectedPart).as(message);
    }

    @Deprecated
    public static void assertEqualsIgnoreCase(String actual, String expected)
    {
        assertThat(actual).isEqualToIgnoringCase(expected);
    }

    @Deprecated
    public static void assertEqualsIgnoreCase(String actual, String expected, String message)
    {
        assertThat(actual).isEqualToIgnoringCase(expected).as(message);
    }

    @Deprecated
    public static void assertNotEquals(Object actual, Object expected)
    {
        Assert.assertNotEquals(actual, expected);
    }

    @Deprecated
    public static void assertNotEquals(Object actual, Object expected, String message)
    {
        Assert.assertNotEquals(actual, expected, message);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertGreaterThan(T actual, T expected)
    {
        assertThat(actual).isGreaterThan(expected);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertGreaterThan(T actual, T expected, String message)
    {
        assertThat(actual).isGreaterThan(expected).as(message);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertGreaterThanOrEqual(T actual, T expected)
    {
        assertThat(actual).isGreaterThanOrEqualTo(expected);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertGreaterThanOrEqual(T actual, T expected, String message)
    {
        assertThat(actual).isGreaterThanOrEqualTo(expected).as(message);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertLessThan(T actual, T expected)
    {
        assertThat(actual).isLessThan(expected);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertLessThan(T actual, T expected, String message)
    {
        assertThat(actual).isLessThan(expected).as(message);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertLessThanOrEqual(T actual, T expected, String message)
    {
        assertThat(actual).isLessThanOrEqualTo(expected).as(message);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertBetweenInclusive(T actual, T lowerBound, T upperBound)
    {
        assertThat(actual).isBetween(lowerBound, upperBound);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertBetweenInclusive(T actual, T lowerBound, T upperBound, String message)
    {
        assertThat(actual).isBetween(lowerBound, upperBound).as(message);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertBetweenExclusive(T actual, T lowerBound, T upperBound)
    {
        assertThat(actual).isStrictlyBetween(lowerBound, upperBound);
    }

    @Deprecated
    public static <T extends Comparable<T>> void assertBetweenExclusive(T actual, T lowerBound, T upperBound, String message)
    {
        assertThat(actual).isStrictlyBetween(lowerBound, upperBound).as(message);
    }

    @Deprecated
    public static <T, U extends T> void assertInstanceOf(T actual, Class<U> expectedType)
    {
        assertThat(actual).isInstanceOf(expectedType);
    }

    @Deprecated
    public static <T, U extends T> void assertInstanceOf(T actual, Class<U> expectedType, String message)
    {
        assertThat(actual).isInstanceOf(expectedType).as(message);
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    public static <T> void assertEqualsIgnoreOrder(Iterable<?> actual, Iterable<?> expected)
    {
        Iterable<T> newActual = (Iterable<T>) actual;
        Iterable<T> newExpected = (Iterable<T>) expected;
        assertThat(newActual).containsAll(newExpected);
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    public static <T> void assertEqualsIgnoreOrder(Iterable<?> actual, Iterable<?> expected, String message)
    {
        Iterable<T> newActual = (Iterable<T>) actual;
        Iterable<T> newExpected = (Iterable<T>) expected;
        assertThat(newActual).containsAll(newExpected).as(message);
    }
}
