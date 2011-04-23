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
package com.proofpoint.testing;

/**
 * Derived from http://code.google.com/p/kawala
 *
 * Licensed under Apache License, Version 2.0
 */

import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.proofpoint.testing.EquivalenceTester.ElementCheckFailure;
import com.proofpoint.testing.EquivalenceTester.PairCheckFailure;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_CLASS_CAST_EXCEPTION;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_EQUAL;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_EQUAL_TO_NULL;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_NOT_EQUAL;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_NOT_REFLEXIVE;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.EQUAL;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.EQUAL_TO_NULL;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.HASH_CODE_NOT_SAME;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.NOT_EQUAL;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.NOT_GREATER_THAN;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.NOT_LESS_THAN;
import static com.proofpoint.testing.EquivalenceTester.EquivalenceFailureType.NOT_REFLEXIVE;
import static org.testng.Assert.assertEquals;
import static org.testng.FileAssert.fail;

public class TestEquivalenceTester
{
    @Test
    public void testCheckFailure()
    {
        assertEquals(new ElementCheckFailure(EQUAL, 0, 0), new ElementCheckFailure(EQUAL, 0, 0));
        assertEquals(new PairCheckFailure(EQUAL, 0, 0, 1, 0), new PairCheckFailure(EQUAL, 0, 0, 1, 0));
    }

    @Test
    public void notEqual()
    {
        try {
            EquivalenceTester.check(newArrayList("foo"), newArrayList("foo"));
        }
        catch (EquivalenceAssertionError e) {
            assertEqualsIgnoreOrder(
                    e.getFailures(),
                    newArrayList(
                            new PairCheckFailure(EQUAL, 0, 0, 1, 0),
                            new PairCheckFailure(EQUAL, 1, 0, 0, 0),
                            new PairCheckFailure(COMPARE_EQUAL, 0, 0, 1, 0),
                            new PairCheckFailure(COMPARE_EQUAL, 1, 0, 0, 0)
                    )
            );
        }
    }

    @Test
    public void notReflexive()
    {
        try {
            EquivalenceTester.check(newArrayList(new NotReflexive()));
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(NOT_REFLEXIVE, 0, 0));
        }
    }

    static class NotReflexive
    {
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(Object that)
        {
            return that != null && this != that;
        }
    }

    @Test
    public void comparableNotReflexive()
    {
        try {
            EquivalenceTester.check(newArrayList(new ComparableNotReflexive()));
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(COMPARE_NOT_REFLEXIVE, 0, 0));
        }
    }

    static class ComparableNotReflexive implements Comparable<ComparableNotReflexive>
    {
        @Override
        public int compareTo(ComparableNotReflexive that)
        {
            Preconditions.checkNotNull(that, "that is null");
            return this == that ? 1 : -1;
        }
    }

    @Test
    public void equalsNull()
    {
        try {
            EquivalenceTester.check(newArrayList(new EqualsNull()));
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(EQUAL_TO_NULL, 0, 0));
        }
    }

    static class EqualsNull
    {
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(Object that)
        {
            return that == null || this == that;
        }
    }

    @Test
    public void nothingCanBeEqualToNull()
    {
        try {
            EquivalenceTester.check(newArrayList(new EqualsDoesNotHandleNullArg()));
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(EQUAL_TO_NULL, 0, 0));
        }
    }

    static class EqualsDoesNotHandleNullArg
    {
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(Object that)
        {
            checkNotNull(that);
            return true;
        }
    }

    @Test
    public void comparableAndNotComparable()
    {
        try {
            EquivalenceTester.check(newArrayList(new NotComparable(), "Hello"));
            fail("EquivalenceTester should have throw an EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 0, 1, 0, 0),
                    new PairCheckFailure(NOT_EQUAL, 0, 0, 0, 1),
                    new PairCheckFailure(NOT_EQUAL, 0, 1, 0, 0),
                    new PairCheckFailure(HASH_CODE_NOT_SAME, 0, 0, 0, 1)
            );
        }

        try {
            EquivalenceTester.check(newArrayList("Hello", new NotComparable()));
            fail("EquivalenceTester should have throw an EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 0, 0, 0, 1),
                    new PairCheckFailure(NOT_EQUAL, 0, 0, 0, 1),
                    new PairCheckFailure(NOT_EQUAL, 0, 1, 0, 0),
                    new PairCheckFailure(HASH_CODE_NOT_SAME, 0, 0, 0, 1)
            );
        }

        try {
            EquivalenceTester.check(newArrayList(new NotComparable()), newArrayList("Hello"));
            fail("EquivalenceTester should have throw an EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 1, 0, 0, 0)
            );
        }

        try {
            EquivalenceTester.check(newArrayList("Hello"), newArrayList(new NotComparable()));
            fail("EquivalenceTester should have throw an EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 0, 0, 1, 0)
            );
        }
    }

    static class NotComparable
    {
    }

    @Test
    public void compareToAgainstNull()
    {
        try {
            EquivalenceTester.checkComparison(newArrayList(new ComparableThatDoesNotThrowNPE(1)));
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new ElementCheckFailure(COMPARE_EQUAL_TO_NULL, 0, 0)
            );
        }
    }

    @Test
    public void testCheckCompare()
    {
        EquivalenceTester.checkComparison(newArrayList(-1), newArrayList(0), newArrayList(1));
        EquivalenceTester.checkComparison(newArrayList("alice"), newArrayList("bob"), newArrayList("charlie"));

        EquivalenceTester.checkComparison(newArrayList(-1, -1, -1), newArrayList(0, 0), newArrayList(1));
        EquivalenceTester.checkComparison(newArrayList("alice"), newArrayList("bob", "bob"), newArrayList("charlie", "charlie", "charlie"));

    }

    @Test
    public void testComparisonOrder()
    {
        try {
            EquivalenceTester.checkComparison(newArrayList(1), newArrayList(0));
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, 0, 0)
            );
        }
        try {
            EquivalenceTester.checkComparison(newArrayList("bob"), newArrayList("alice"));
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, 0, 0)
            );
        }
        try {
            EquivalenceTester.checkComparison(newArrayList(1), newArrayList(0, 0));
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, 0, 0),
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 1),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 1, 0, 0)
            );
        }
        try {
            EquivalenceTester.checkComparison(newArrayList("bob"), newArrayList("alice", "alice"));
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, 0, 0),
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 1),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 1, 0, 0)
            );
        }

    }

    @Test
    public void testComparisonNotEquals()
    {
        try {
            EquivalenceTester.checkComparison(newArrayList(0), newArrayList(1, 2));
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_EQUAL, 1, 0, 1, 1),
                    new PairCheckFailure(NOT_EQUAL, 1, 1, 1, 0),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 1, 0, 1, 1),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 1, 1, 1, 0),
                    new PairCheckFailure(HASH_CODE_NOT_SAME, 1, 0, 1, 1)
            );
        }
        try {
            EquivalenceTester.checkComparison(newArrayList("alice"), newArrayList("bob", "charlie"));
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_EQUAL, 1, 0, 1, 1),
                    new PairCheckFailure(NOT_EQUAL, 1, 1, 1, 0),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 1, 0, 1, 1),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 1, 1, 1, 0),
                    new PairCheckFailure(HASH_CODE_NOT_SAME, 1, 0, 1, 1)
            );
        }

    }

    @Test
    @SuppressWarnings({"RawUseOfParameterizedType", "RedundantCast"})
    public void testNotComparableComparison()
    {
        try {
            EquivalenceTester.checkComparison((List) newArrayList(1), (List) newArrayList("string"));
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 0, 0, 1, 0),
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 1, 0, 0, 0)
            );
        }
    }

    static class ComparableThatDoesNotThrowNPE
        implements Comparable<ComparableThatDoesNotThrowNPE>
    {
        private final int value;

        public ComparableThatDoesNotThrowNPE(int value)
        {
            this.value = value;
        }

        @Override
        public int compareTo(ComparableThatDoesNotThrowNPE o)
        {
            if (o == null) {
                return 1;
            }

            return ComparisonChain.start().compare(value, o.value).result();
        }

        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(Object that)
        {
            return that != null && value == ((ComparableThatDoesNotThrowNPE) that).value;
        }

        @Override
        public int hashCode()
        {
            return value;
        }
    }

    private void assertExpectedFailures(EquivalenceAssertionError e, ElementCheckFailure... expected)
    {
        assertEqualsIgnoreOrder(e.getFailures(), newArrayList(expected));
    }
}