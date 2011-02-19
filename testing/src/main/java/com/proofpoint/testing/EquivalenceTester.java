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
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Equivalence tester streamlining tests of {@link #equals} and {@link #hashCode} methods. Using this tester makes it
 * easy to verify that {@link #equals} is indeed an <a href="http://en.wikipedia.org/wiki/Equivalence_relation">equivalence
 * relation</a> (reflexive, symmetric and transitive). It also verifies that equality between two objects implies hash
 * code equality, as required by the {@link #hashCode()} contract.
 */
public final class EquivalenceTester
{
    private EquivalenceTester()
    {
    }

    @SuppressWarnings({"ObjectEqualsNull"})
    public static void check(Collection<?>... equivalenceClasses)
    {
        List<List<Object>> ec =
                newArrayListWithExpectedSize(equivalenceClasses.length);

        // nothing can be equal to null
        for (Collection<?> congruenceClass : equivalenceClasses) {
            for (Object element : congruenceClass) {
                try {
                    assertFalse(element.equals(null),
                            format("%s can not be equal to null", element)
                    );
                }
                catch (NullPointerException e) {
                    throw new AssertionError(
                            format("NullPointerException when comparing %s to null", element));
                }
            }
        }

        // reflexivity
        for (Collection<?> congruenceClass : equivalenceClasses) {
            List<Object> c = newArrayList();
            ec.add(c);
            for (Object element : congruenceClass) {
                assertTrue(element.equals(element),
                        format("reflexivity of %s", element));
                compareShouldReturn0(element, element);
                c.add(element);
            }
        }

        // equality within congruence classes
        for (List<Object> c : ec) {
            for (int i = 0; i < c.size(); i++) {
                Object e1 = c.get(i);
                for (int j = i + 1; j < c.size(); j++) {
                    Object e2 = c.get(j);
                    assertTrue(e1.equals(e2), format("%s=%s", e1, e2));
                    assertTrue(e2.equals(e1), format("%s=%s", e2, e1));
                    compareShouldReturn0(e1, e2);
                    compareShouldReturn0(e2, e1);
                    assertEquals(e1.hashCode(), e2.hashCode(), format("hashCode %s vs. %s", e1, e2));
                }
            }
        }

        // inequality across congruence classes
        for (int i = 0; i < ec.size(); i++) {
            List<Object> c1 = ec.get(i);
            for (int j = i + 1; j < ec.size(); j++) {
                List<Object> c2 = ec.get(j);
                for (Object e1 : c1) {
                    for (Object e2 : c2) {
                        assertFalse(e1.equals(e2), format("%s!=%s", e1, e2));
                        assertFalse(e2.equals(e1), format("%s!=%s", e2, e1));
                        compareShouldNotReturn0(e1, e2);
                        compareShouldNotReturn0(e2, e1);
                    }
                }
            }
        }
    }

    public static <T extends Comparable<T>> void checkComparison(Collection<T>... equivalenceClasses)
    {
        check(equivalenceClasses);

        for (int i = 0; i < equivalenceClasses.length; i++) {
            Collection<T> lesserBag = equivalenceClasses[i];
            for (int j = i + 1; j < equivalenceClasses.length; j++) {
                Collection<T> greaterBag = equivalenceClasses[j];
                checkComparison(lesserBag, greaterBag);
            }
        }

    }

    private static <T extends Comparable<T>> void checkComparison(Collection<T> lesserBag, Collection<T> greaterBag)
    {
        for (T lesser : lesserBag) {
            for (T greater : greaterBag) {
                Assertions.assertLessThan(lesser, greater);
                Assertions.assertGreaterThan(greater, lesser);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void compareShouldReturn0(Object e1, Object e2)
    {
        if (e1 instanceof Comparable<?>) {
            assertTrue(((Comparable<Object>) e1).compareTo(e2) == 0,
                    format("comparison should return 0 for %s and %s", e1, e2));
        }
    }

    @SuppressWarnings("unchecked")
    private static void compareShouldNotReturn0(Object e1, Object e2)
    {
        if (e1 instanceof Comparable<?>) {
            assertFalse(((Comparable<Object>) e1).compareTo(e2) == 0,
                    format("comparison should not return 0 for %s and %s", e1, e2));
        }
    }

}