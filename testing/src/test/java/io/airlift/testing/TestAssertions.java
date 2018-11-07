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

import com.google.common.collect.Sets;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.airlift.testing.Assertions.assertBetweenExclusive;
import static io.airlift.testing.Assertions.assertBetweenInclusive;
import static io.airlift.testing.Assertions.assertContains;
import static io.airlift.testing.Assertions.assertEqualsIgnoreCase;
import static io.airlift.testing.Assertions.assertEqualsIgnoreOrder;
import static io.airlift.testing.Assertions.assertGreaterThan;
import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static io.airlift.testing.Assertions.assertLessThan;
import static io.airlift.testing.Assertions.assertLessThanOrEqual;
import static io.airlift.testing.TestAssertions.SubComparable.createSubComparable;
import static io.airlift.testing.TestAssertions.SuperComparable.createSuperComparable;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

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
        assertContains(actual, expected);
        assertContains(actual, expected, MESSAGE);
    }

    private void failContains(String actual, String expected)
    {
        try {
            assertContains(actual, expected);
            fail("Expected AssertionError"); // TODO: bug... this will throw AssertionError
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }

        try {
            assertContains(actual, expected, MESSAGE);
            fail("Expected AssertionError");
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
        assertEqualsIgnoreCase(actual, expected);
        assertEqualsIgnoreCase(expected, actual);
        assertEqualsIgnoreCase(actual, expected, MESSAGE);
        assertEqualsIgnoreCase(expected, actual, MESSAGE);
    }

    private void failEqualsIgnoreCase(String actual, String expected)
    {
        try {
            assertEqualsIgnoreCase(actual, expected);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }

        try {
            assertEqualsIgnoreCase(actual, expected, MESSAGE);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            // success
            verifyExceptionMessage(e, MESSAGE, actual, expected);
        }
    }

    @Test
    public void testAssertInstanceof()
    {
        passInstanceOf("hello", Object.class);
        passInstanceOf("hello", String.class);
        passInstanceOf("hello", Serializable.class);
        passInstanceOf("hello", CharSequence.class);
        passInstanceOf("hello", Comparable.class);
        passInstanceOf(42, Integer.class);

        failInstanceOf("hello", Integer.class);
    }

    private void passInstanceOf(Object actual, Class<?> expectedType)
    {
        assertInstanceOf(actual, expectedType);
        assertInstanceOf(actual, expectedType, MESSAGE);
    }

    private void failInstanceOf(Object actual, Class<?> expectedType)
    {
        try {
            assertInstanceOf(actual, expectedType);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expectedType.getName());
        }

        try {
            assertInstanceOf(actual, expectedType, MESSAGE);
            fail("Expected AssertionError");
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void passGreaterThan(Comparable actual, Comparable expected)
    {
        assertGreaterThan(actual, expected);
        assertGreaterThan(actual, expected, MESSAGE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void failGreaterThan(Comparable actual, Comparable expected)
    {
        try {
            assertGreaterThan(actual, expected);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }
        try {
            assertGreaterThan(actual, expected, MESSAGE);
            fail("Expected AssertionError");
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

    @Test
    public void testEqualsIgnoreOrder()
    {
        passEqualsIgnoreOrder(Collections.emptyList(), Collections.emptyList());
        passEqualsIgnoreOrder(Arrays.asList(1, 2, 3), Arrays.asList(1, 2, 3));
        passEqualsIgnoreOrder(Arrays.asList(3, 2, 1), Arrays.asList(1, 2, 3));
        passEqualsIgnoreOrder(Arrays.asList(1, 1, 1), Arrays.asList(1, 1, 1));
        passEqualsIgnoreOrder(Arrays.asList(1, 2, 3), Sets.newHashSet(1, 2, 3));

        List<Integer> list = Arrays.asList(3, 2, 1);
        passEqualsIgnoreOrder(list, list);

        failEqualsIgnoreOrder(Arrays.asList(1, 1, 1), Arrays.asList(1, 1));
        failEqualsIgnoreOrder(Collections.emptyList(), Arrays.asList(1, 2, 3));
        failEqualsIgnoreOrder(Arrays.asList(4, 5, 6, 7), Arrays.asList(1, 2, 3));
        failEqualsIgnoreOrder(Arrays.asList(1, 2, 3, 4), Arrays.asList(1, 2, 3));
        failEqualsIgnoreOrder(Arrays.asList(1, 2, 3), Arrays.asList(1, 2, 3, 4));
    }

    private void passEqualsIgnoreOrder(Iterable<?> actual, Iterable<?> expected)
    {
        assertEqualsIgnoreOrder(actual, expected);
        assertEqualsIgnoreOrder(actual, expected, MESSAGE);
    }

    private void failEqualsIgnoreOrder(Iterable<?> actual, Iterable<?> expected)
    {
        try {
            assertEqualsIgnoreOrder(actual, expected);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessageList(e, null, actual, expected);
        }
        try {
            assertEqualsIgnoreOrder(actual, expected, MESSAGE);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessageList(e, MESSAGE, actual, expected);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void passGreaterThanOrEqual(Comparable actual, Comparable expected)
    {
        assertGreaterThanOrEqual(actual, expected);
        assertGreaterThanOrEqual(actual, expected, MESSAGE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void failGreaterThanOrEqual(Comparable actual, Comparable expected)
    {
        try {
            assertGreaterThanOrEqual(actual, expected);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }
        try {
            assertGreaterThanOrEqual(actual, expected, MESSAGE);
            fail("Expected AssertionError");
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void passLessThan(Comparable actual, Comparable expected)
    {
        assertLessThan(actual, expected);
        assertLessThan(actual, expected, MESSAGE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void failLessThan(Comparable actual, Comparable expected)
    {
        try {
            assertLessThan(actual, expected);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }
        try {
            assertLessThan(actual, expected, MESSAGE);
            fail("Expected AssertionError");
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void passLessThanOrEqual(Comparable actual, Comparable expected)
    {
        assertLessThanOrEqual(actual, expected);
        assertLessThanOrEqual(actual, expected, MESSAGE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void failLessThanOrEqual(Comparable actual, Comparable expected)
    {
        try {
            assertLessThanOrEqual(actual, expected);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, expected);
        }
        try {
            assertLessThanOrEqual(actual, expected, MESSAGE);
            fail("Expected AssertionError");
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void passBetweenInclusive(Comparable actual, Comparable lowerBound, Comparable upperBound)
    {
        assertBetweenInclusive(actual, lowerBound, upperBound);
        assertBetweenInclusive(actual, lowerBound, upperBound, MESSAGE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void failBetweenInclusive(Comparable actual, Comparable lowerBound, Comparable upperBound)
    {
        try {
            assertBetweenInclusive(actual, lowerBound, upperBound);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, lowerBound, upperBound);
        }
        try {
            assertBetweenInclusive(actual, lowerBound, upperBound, MESSAGE);
            fail("Expected AssertionError");
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void passBetweenExclusive(Comparable actual, Comparable lowerBound, Comparable upperBound)
    {
        assertBetweenExclusive(actual, lowerBound, upperBound);
        assertBetweenExclusive(actual, lowerBound, upperBound, MESSAGE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void failBetweenExclusive(Comparable actual, Comparable lowerBound, Comparable upperBound)
    {
        try {
            assertBetweenExclusive(actual, lowerBound, upperBound);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, null, actual, lowerBound, upperBound);
        }
        try {
            assertBetweenExclusive(actual, lowerBound, upperBound, MESSAGE);
            fail("Expected AssertionError");
        }
        catch (AssertionError e) {
            verifyExceptionMessage(e, MESSAGE, actual, lowerBound, upperBound);
        }
    }

    private void verifyExceptionMessage(AssertionError e, String message, Object... values)
    {
        assertNotNull(e);
        String actualMessage = e.getMessage();
        assertNotNull(actualMessage);
        if (message != null) {
            assertTrue(actualMessage.startsWith(message + " "));
        }
        else {
            assertFalse(actualMessage.startsWith(" "));
        }

        for (Object value : values) {
            assertTrue(actualMessage.contains("<" + value + ">"));
        }
    }

    private void verifyExceptionMessageList(AssertionError e, String message, Iterable<?>... lists)
    {
        assertNotNull(e);
        String actualMessage = e.getMessage();
        assertNotNull(actualMessage);
        if (message != null) {
            assertTrue(actualMessage.startsWith(message + " "));
        }
        else {
            assertFalse(actualMessage.startsWith(" "));
        }

        for (Iterable<?> values : lists) {
            for (Object value : values) {
                assertTrue(actualMessage.contains(value.toString()));
            }
        }
    }

    public static class SuperComparable<T extends Comparable<T>>
            implements Comparable<SuperComparable<T>>
    {
        public static <T extends Comparable<T>> SuperComparable<T> createSuperComparable(T value)
        {
            return new SuperComparable<>(value);
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

    public static class SubComparable<T extends Comparable<T>>
            extends SuperComparable<T>
    {
        public static <T extends Comparable<T>> SubComparable<T> createSubComparable(T value)
        {
            return new SubComparable<>(value);
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
