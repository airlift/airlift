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

/**
 * Derived from http://code.google.com/p/kawala
 *
 * Licensed under Apache License, Version 2.0
 */

import com.google.common.collect.ComparisonChain;
import io.airlift.testing.EquivalenceTester.ElementCheckFailure;
import io.airlift.testing.EquivalenceTester.PairCheckFailure;
import org.testng.annotations.Test;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.airlift.testing.Assertions.assertEqualsIgnoreOrder;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_CLASS_CAST_EXCEPTION;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_EQUAL;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_EQUAL_TO_NULL;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_NOT_EQUAL;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.COMPARE_NOT_REFLEXIVE;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.EQUAL;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.EQUAL_NULL_EXCEPTION;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.EQUAL_TO_NULL;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.EQUAL_TO_UNRELATED_CLASS;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.EQUAL_TO_UNRELATED_CLASS_CLASS_CAST_EXCEPTION;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.HASH_CODE_NOT_SAME;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.NOT_EQUAL;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.NOT_GREATER_THAN;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.NOT_LESS_THAN;
import static io.airlift.testing.EquivalenceTester.EquivalenceFailureType.NOT_REFLEXIVE;
import static io.airlift.testing.EquivalenceTester.comparisonTester;
import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static java.util.Objects.requireNonNull;
import static org.testng.Assert.assertEquals;
import static org.testng.FileAssert.fail;

public class TestEquivalenceTester
{
    @Test
    public void testCheckFailure()
    {
        Object o1 = new Object();
        Object o2 = new Object();
        assertEquals(new ElementCheckFailure(EQUAL, 0, 0, o1), new ElementCheckFailure(EQUAL, 0, 0, o1));
        assertEquals(new PairCheckFailure(EQUAL, 0, 0, o1, 1, 0, o2), new PairCheckFailure(EQUAL, 0, 0, o1, 1, 0, o2));
    }

    @Test
    public void notEqual()
    {
        try {
            equivalenceTester()
                    .addEquivalentGroup("foo")
                    .addEquivalentGroup("foo")
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertEqualsIgnoreOrder(
                    e.getFailures(),
                    newArrayList(
                            new PairCheckFailure(EQUAL, 0, 0, "foo", 1, 0, "foo"),
                            new PairCheckFailure(EQUAL, 1, 0, "foo", 0, 0, "foo"),
                            new PairCheckFailure(COMPARE_EQUAL, 0, 0, "foo", 1, 0, "foo"),
                            new PairCheckFailure(COMPARE_EQUAL, 1, 0, "foo", 0, 0, "foo")
                    )
            );
        }
    }

    @Test
    public void notReflexive()
    {
        NotReflexive notReflexive = new NotReflexive();
        try {
            equivalenceTester()
                    .addEquivalentGroup(notReflexive)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(NOT_REFLEXIVE, 0, 0, notReflexive));
        }
    }

    static class NotReflexive
    {
        public boolean equals(Object that)
        {
            return that != null && that instanceof NotReflexive && this != that;
        }
    }

    @Test
    public void comparableNotReflexive()
    {
        ComparableNotReflexive comparableNotReflexive = new ComparableNotReflexive();
        try {
            equivalenceTester()
                    .addEquivalentGroup(comparableNotReflexive)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(COMPARE_NOT_REFLEXIVE, 0, 0, comparableNotReflexive));
        }
    }

    static class ComparableNotReflexive implements Comparable<ComparableNotReflexive>
    {
        @Override
        public int compareTo(ComparableNotReflexive that)
        {
            requireNonNull(that, "that is null");
            return this == that ? 1 : -1;
        }
    }

    @Test
    public void notSymmetric()
    {
        NotSymmetric o1 = new NotSymmetric(1);
        NotSymmetric o2 = new NotSymmetric(2);
        NotSymmetric o3 = new NotSymmetric(3);
        try {
            equivalenceTester()
                    .addEquivalentGroup(o1, o3, o2)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_EQUAL, 0, 0, o1, 0, 1, o3),
                    new PairCheckFailure(NOT_EQUAL, 0, 0, o1, 0, 2, o2),
                    new PairCheckFailure(NOT_EQUAL, 0, 2, o2, 0, 1, o3));
        }
    }

    static class NotSymmetric
    {
        private int id;

        NotSymmetric(int id)
        {
            this.id = id;
        }

        public boolean equals(Object that)
        {
            return that != null && that instanceof NotSymmetric && id >= ((NotSymmetric) that).id;
        }

        @Override
        public int hashCode()
        {
            return 0;
        }
    }

    @Test
    public void comparableNotSymmetric()
    {
        ComparableNotSymmetric o1 = new ComparableNotSymmetric(1);
        ComparableNotSymmetric o2 = new ComparableNotSymmetric(2);
        ComparableNotSymmetric o3 = new ComparableNotSymmetric(3);
        try {
            equivalenceTester()
                    .addEquivalentGroup(o1, o3, o2)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 0, 0, o1, 0, 1, o3),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 0, 0, o1, 0, 2, o2),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 0, 2, o2, 0, 1, o3));
        }
    }

    static class ComparableNotSymmetric implements Comparable<ComparableNotSymmetric>
    {
        private int id;

        ComparableNotSymmetric(int id)
        {
            this.id = id;
        }

        @Override
        public int compareTo(ComparableNotSymmetric that)
        {
            requireNonNull(that, "that is null");
            if (id >= that.id) {
                return 0;
            }
            return -1;
        }

        public boolean equals(Object that)
        {
            return that != null && that instanceof ComparableNotSymmetric;
        }

        @Override
        public int hashCode()
        {
            return 0;
        }
    }

    @Test
    public void equalsNull()
    {
        EqualsNull equalsNull = new EqualsNull();
        try {
            equivalenceTester()
                    .addEquivalentGroup(equalsNull)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(EQUAL_TO_NULL, 0, 0, equalsNull));
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
    public void equalsNullThrowsException()
    {
        EqualsNullThrowsException equalsNullThrowsException = new EqualsNullThrowsException();
        try {
            equivalenceTester()
                    .addEquivalentGroup(equalsNullThrowsException)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(EQUAL_NULL_EXCEPTION, 0, 0, equalsNullThrowsException));
        }
    }

    static class EqualsNullThrowsException
    {
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(Object that)
        {
            return this.hashCode() == that.hashCode();
        }
    }

    @Test
    public void equalsUnrelatedClass()
    {
        EqualsUnrelatedClass equalsUnrelatedClass = new EqualsUnrelatedClass();
        try {
            equivalenceTester()
                    .addEquivalentGroup(equalsUnrelatedClass)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(EQUAL_TO_UNRELATED_CLASS, 0, 0, equalsUnrelatedClass));
        }
    }

    static class EqualsUnrelatedClass
    {
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(Object that)
        {
            return that != null;
        }
    }

    @Test
    public void equalsUnrelatedClassThrowsException()
    {
        EqualsOtherClassThrowsException equalsOtherClassThrowsException = new EqualsOtherClassThrowsException();
        try {
            equivalenceTester()
                    .addEquivalentGroup(equalsOtherClassThrowsException)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e, new ElementCheckFailure(EQUAL_TO_UNRELATED_CLASS_CLASS_CAST_EXCEPTION, 0, 0, equalsOtherClassThrowsException));
        }
    }

    static class EqualsOtherClassThrowsException
    {
        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(Object that)
        {
            return that != null && ((EqualsOtherClassThrowsException) that).hashCode() == this.hashCode();
        }
    }

    @Test
    public void comparableAndNotComparable()
    {
        NotComparable notComparable = new NotComparable();
        try {
            equivalenceTester()
                    .addEquivalentGroup(notComparable, "Hello")
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 0, 1, "Hello", 0, 0, notComparable),
                    new PairCheckFailure(NOT_EQUAL, 0, 0, notComparable, 0, 1, "Hello"),
                    new PairCheckFailure(NOT_EQUAL, 0, 1, "Hello", 0, 0, notComparable),
                    new PairCheckFailure(HASH_CODE_NOT_SAME, 0, 0, notComparable, 0, 1, "Hello")
            );
        }

        try {
            equivalenceTester()
                    .addEquivalentGroup("Hello", notComparable)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 0, 0, "Hello", 0, 1, notComparable),
                    new PairCheckFailure(NOT_EQUAL, 0, 0, "Hello", 0, 1, notComparable),
                    new PairCheckFailure(NOT_EQUAL, 0, 1, notComparable, 0, 0, "Hello"),
                    new PairCheckFailure(HASH_CODE_NOT_SAME, 0, 0, "Hello", 0, 1, notComparable)
            );
        }

        try {
            equivalenceTester()
                    .addEquivalentGroup(notComparable)
                    .addEquivalentGroup("Hello")
                    .check();
            fail("EquivalenceTester should have throw an EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 1, 0, "Hello", 0, 0, notComparable)
            );
        }

        try {
            equivalenceTester()
                    .addEquivalentGroup("Hello")
                    .addEquivalentGroup(notComparable)
                    .check();
            fail("EquivalenceTester should have throw an EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 0, 0, "Hello", 1, 0, notComparable)
            );
        }
    }

    static class NotComparable
    {
    }

    @Test
    public void compareToAgainstNull()
    {
        ComparableThatDoesNotThrowNPE comparableThatDoesNotThrowNPE = new ComparableThatDoesNotThrowNPE(1);
        try {
            equivalenceTester()
                    .addEquivalentGroup(comparableThatDoesNotThrowNPE)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new ElementCheckFailure(COMPARE_EQUAL_TO_NULL, 0, 0, comparableThatDoesNotThrowNPE)
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
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 1, 0, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, 0, 0, 0, 1)
            );
        }
        try {
            comparisonTester()
                    .addLesserGroup("bob")
                    .addGreaterGroup("alice")
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, "bob", 1, 0, "alice"),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, "alice", 0, 0, "bob")
            );
        }
        try {
            comparisonTester()
                    .addLesserGroup(1)
                    .addGreaterGroup(0, 0)
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 1, 0, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, 0, 0, 0, 1),
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, 1, 1, 1, 0),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 1, 0, 0, 0, 1)
            );
        }
        try {
            comparisonTester()
                    .addLesserGroup("bob")
                    .addGreaterGroup("alice", "alice")
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, "bob", 1, 0, "alice"),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 0, "alice", 0, 0, "bob"),
                    new PairCheckFailure(NOT_LESS_THAN, 0, 0, "bob", 1, 1, "alice"),
                    new PairCheckFailure(NOT_GREATER_THAN, 1, 1, "alice", 0, 0, "bob")
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
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_EQUAL, 1, 0, 1, 1, 1, 2),
                    new PairCheckFailure(NOT_EQUAL, 1, 1, 2, 1, 0, 1),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 1, 0, 1, 1, 1, 2),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 1, 1, 2, 1, 0, 1),
                    new PairCheckFailure(HASH_CODE_NOT_SAME, 1, 0, 1, 1, 1, 2)
            );
        }
        try {
            comparisonTester()
                    .addLesserGroup("alice")
                    .addGreaterGroup("bob", "charlie")
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(NOT_EQUAL, 1, 0, "bob", 1, 1, "charlie"),
                    new PairCheckFailure(NOT_EQUAL, 1, 1, "charlie", 1, 0, "bob"),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 1, 0, "bob", 1, 1, "charlie"),
                    new PairCheckFailure(COMPARE_NOT_EQUAL, 1, 1, "charlie", 1, 0, "bob"),
                    new PairCheckFailure(HASH_CODE_NOT_SAME, 1, 0, "bob", 1, 1, "charlie")
            );
        }

    }

    @Test
    @SuppressWarnings({"RawUseOfParameterizedType", "RedundantCast"})
    public void testNotComparableComparison()
    {
        try {
            comparisonTester()
                    .addLesserGroup((List) newArrayList(5)) // cast to List in order to remove type safety of returned generic
                    .addGreaterGroup("string")
                    .check();
            fail("Expected EquivalenceAssertionError");
        }
        catch (EquivalenceAssertionError e) {
            assertExpectedFailures(e,
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 0, 0, 5, 1, 0, "string"),
                    new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, 1, 0, "string", 0, 0, 5)
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
            return that != null && that instanceof ComparableThatDoesNotThrowNPE && value == ((ComparableThatDoesNotThrowNPE) that).value;
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
