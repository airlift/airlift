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
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static org.testng.FileAssert.fail;

public class TestEquivalenceTester
{

    @Test(expectedExceptions = AssertionError.class)
    public void nothingCanBeEqualToNull()
    {
        EquivalenceTester.check(newArrayList(new EqualsDoesNotHandleNullArg()));
    }

    static class EqualsDoesNotHandleNullArg
    {
        @Override
        public boolean equals(Object that)
        {
            checkNotNull(that);
            return true;
        }
    }

    @Test
    public void comparisonCompliance()
    {
        EquivalenceTester.check(
                newArrayList(weird(0, 2), weird(0, 12)),
                newArrayList(weird(0, 5), weird(0, 15)));
    }

    @Test
    public void comparisonCompliance_wontReturn0()
    {
        try {
            EquivalenceTester.check(
                    newArrayList(weird(1, 2), weird(2, 12)),
                    newArrayList(weird(2, 5), weird(0, 15)));
            fail("Comparison should have returned non-zero");
        }
        catch (Throwable t) {
            // as expected
        }
    }

    @Test(expectedExceptions = ClassCastException.class)
    public void comparableAndNotComparable()
    {
        EquivalenceTester.check(
                newArrayList(new NotComparable()),
                newArrayList("Hello"));
    }

    @Test
    public void compareToAgainstNull()
    {
        failCheckComparison(newArrayList(new ComparableThatDoesNotThrowNPE(1), new ComparableThatDoesNotThrowNPE(1), new ComparableThatDoesNotThrowNPE(1)));
    }

    @Test
    public void testCheckCompare()
    {
        passCheckComparison(newArrayList(-1), newArrayList(0), newArrayList(1));
        passCheckComparison(newArrayList("alice"), newArrayList("bob"), newArrayList("charlie"));
        failCheckComparison(newArrayList(1), newArrayList(0), newArrayList(-1));
        failCheckComparison(newArrayList("charlie"), newArrayList("bob"), newArrayList("alice"));

        passCheckComparison(newArrayList(-1, -1, -1), newArrayList(0, 0), newArrayList(1));
        passCheckComparison(newArrayList("alice"), newArrayList("bob", "bob"), newArrayList("charlie", "charlie", "charlie"));
        failCheckComparison(newArrayList(1), newArrayList(0, 0), newArrayList(-1, -1, -1));
        failCheckComparison(newArrayList("charlie"), newArrayList("bob", "bob"), newArrayList("alice", "alice", "alice"));

        failCheckComparison(newArrayList(-1, 0), newArrayList(1));
        failCheckComparison(newArrayList(-1), newArrayList(0, 1));
    }

    private <T extends Comparable<T>> void passCheckComparison(Collection<T>... equivalenceClasses)
    {
        EquivalenceTester.checkComparison(equivalenceClasses);
    }

    private <T extends Comparable<T>> void failCheckComparison(Collection<T>... equivalenceClasses)
    {
        boolean assertionFailed = false;
        try {
            EquivalenceTester.checkComparison(equivalenceClasses);
        }
        catch (AssertionError e) {
            assertionFailed = true;
        }

        if (!assertionFailed) {
            Assert.fail("Expected AssertionError");
        }
    }

    WeirdClass weird(int shift, int value)
    {
        return new WeirdClass(shift, value);
    }

    static class WeirdClass
            implements Comparable<WeirdClass>
    {
        private int shift;
        private int value;

        WeirdClass(int shift, int value)
        {
            this.shift = shift;
            this.value = value;
        }

        @Override
        public int compareTo(WeirdClass other)
        {
            return (shift + value) % 10 - (other.shift + other.value) % 10;
        }

        public boolean equals(Object o)
        {
            return o instanceof WeirdClass
                    && (value - ((WeirdClass) o).value) % 10 == 0;
        }

        public int hashCode()
        {
            return (((shift + value) % 10) + 3);
        }

        public String toString()
        {
            return "WeirdClass(" + shift + "," + value + ")";
        }
    }

    static class NotComparable
    {
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

        @Override
        public boolean equals(Object o)
        {
            if (o == null) {
                return false;
            }
            return value == ((ComparableThatDoesNotThrowNPE) o).value;
        }

        @Override
        public int hashCode()
        {
            return value;
        }
    }
}