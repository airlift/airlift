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
import static com.proofpoint.testing.EquivalenceTester.comparisonTester;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;
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
            equivalenceTester()
                    .addEquivalentGroup("foo")
                    .addEquivalentGroup("foo")
                    .check();
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
            equivalenceTester()
                    .addEquivalentGroup(new NotReflexive())
                    .check();
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
            equivalenceTester()
                    .addEquivalentGroup(new ComparableNotReflexive())
                    .check();
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
            equivalenceTester()
                    .addEquivalentGroup(new EqualsNull())
                    .check();
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
            equivalenceTester()
                    .addEquivalentGroup(new EqualsDoesNotHandleNullArg())
                    .check();
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
            equivalenceTester()
                    .addEquivalentGroup(new NotComparable(), "Hello")
                    .check();
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
            equivalenceTester()
                    .addEquivalentGroup("Hello", new NotComparable())
                    .check();
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
            equivalenceTester()
                    .addEquivalentGroup(new NotComparable())
                    .addEquivalentGroup("Hello")
                    .check();
            fail("EquivalenceTester should have throw an EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 1, 0, 0, 0)
            );
        }

        try {
            equivalenceTester()
                    .addEquivalentGroup("Hello")
                    .addEquivalentGroup(new NotComparable())
                    .check();
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
            equivalenceTester()
                    .addEquivalentGroup(new ComparableThatDoesNotThrowNPE(1))
                    .check();
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
        comparisonTester()
                .addLesserGroup(-1)
                .addGreaterGroup(0)
                .addGreaterGroup(1)
                .check();

        comparisonTester()
                .addLesserGroup("alice")
                .addGreaterGroup("bob")
                .addGreaterGroup("charlie")
                .check();

        comparisonTester()
                .addLesserGroup(-1, -1, -1)
                .addGreaterGroup(0, 0)
                .addGreaterGroup(1)
                .check();

        comparisonTester()
                .addLesserGroup("alice")
                .addGreaterGroup("bob", "bob")
                .addGreaterGroup("charlie", "charlie", "charlie")
                .check();

    }

    @Test
    public void testComparisonOrder()
    {
        try {
            comparisonTester()
                    .addLesserGroup(1)
                    .addGreaterGroup(0)
                    .check();
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, 0, 0)
            );
        }
        try {
            comparisonTester()
                    .addLesserGroup("bob")
                    .addGreaterGroup("alice")
                    .check();
            Assert.fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, 0, 0)
            );
        }
        try {
            comparisonTester()
                    .addLesserGroup(1)
                    .addGreaterGroup(0, 0)
                    .check();
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
            comparisonTester()
                    .addLesserGroup("bob")
                    .addGreaterGroup("alice", "alice")
                    .check();
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
            comparisonTester()
                    .addLesserGroup(0)
                    .addGreaterGroup(1, 2)
                    .check();
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
            comparisonTester()
                    .addLesserGroup("alice")
                    .addGreaterGroup("bob", "charlie")
                    .check();
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
            comparisonTester()
                    .addLesserGroup((List) newArrayList(1))
                    .addGreaterGroup("string")
                    .check();
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