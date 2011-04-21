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
import org.testng.Assert;

import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

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

        int classNumber = 0;
        for (Collection<?> congruenceClass : equivalenceClasses) {
            int elementNumber = 0;
            for (Object element : congruenceClass) {
                // nothing can be equal to null
                try {
                    assertFalse(element.equals(null),
                            format("Element at position (%d, %d) returns true when compared to null via equals()", classNumber, elementNumber)
                    );
                }
                catch (NullPointerException e) {
                    fail(format("Element at position (%d, %d) throws NullPointerException when comparing to null via equals()", classNumber, elementNumber));
                }

                // if a class implements comparable, object.compareTo(null) must throw NPE
                if (element instanceof Comparable) {
                    try {
                        ((Comparable<?>) element).compareTo(null);
                        fail(format("Element at position (%d, %d) implements Comparable but does not throw NullPointerException when compared to null", classNumber, elementNumber));
                    }
                    catch (NullPointerException e) {
                        // ok
                    }
                }

                ++elementNumber;
            }

            ++classNumber;
        }

        // reflexivity
        classNumber = 0;
        for (Collection<?> congruenceClass : equivalenceClasses) {
            List<Object> c = newArrayList();
            ec.add(c);

            int elementNumber = 0;
            for (Object element : congruenceClass) {
                ++elementNumber;
                assertTrue(element.equals(element), format("Element at position (%d, %d) is not equal to itself when compared via equals()", classNumber, elementNumber));
                compareShouldReturn0(element, element, "Element at position (%d, %d) implements Comparable but does not return 0 when compared to itself");
                c.add(element);
            }
            ++classNumber;
        }

        // equality within congruence classes
        classNumber = 0;
        for (List<Object> c : ec) {
            for (int i = 0; i < c.size(); i++) {
                Object e1 = c.get(i);
                for (int j = i + 1; j < c.size(); j++) {
                    Object e2 = c.get(j);
                    assertTrue(e1.equals(e2), format("Element at position (%d, %d) is not equal to element (%d, %d) when compared via equals()", classNumber, i, classNumber, j));
                    assertTrue(e2.equals(e1), format("Element at position (%d, %d) is not equal to element (%d, %d) when compared via equals()", classNumber, j, classNumber, i));
                    compareShouldReturn0(e1, e2, format("Element at position (%d, %d) implements Comparable and does not return 0 when compared to element (%d, %d)", classNumber, i, classNumber, j));
                    compareShouldReturn0(e2, e1, format("Element at position (%d, %d) implements Comparable and does not return 0 when compared to element (%d, %d)", classNumber, j, classNumber, i));
                    assertEquals(e1.hashCode(), e2.hashCode(), format("Elements at position (%d, %d) and (%d, %d) have different hash codes", classNumber, i, classNumber, j));
                }
            }
            ++classNumber;
        }

        // inequality across congruence classes
        classNumber = 0;
        for (int i = 0; i < ec.size(); i++) {
            List<Object> c1 = ec.get(i);
            for (int j = i + 1; j < ec.size(); j++) {
                List<Object> c2 = ec.get(j);
                for (Object e1 : c1) {
                    for (Object e2 : c2) {
                        assertFalse(e1.equals(e2), format("Element at position (%d, %d) is equal to element (%d, %d) when compared via equals()", classNumber, i, classNumber, j));
                        assertFalse(e2.equals(e1), format("Element at position (%d, %d) is equal to element (%d, %d) when compared via equals()", classNumber, j, classNumber, i));
                        compareShouldNotReturn0(e1, e2, format("Element at position (%d, %d) implements Comparable and returns 0 when compared to element (%d, %d)", classNumber, i, classNumber, j));
                        compareShouldNotReturn0(e2, e1, format("Element at position (%d, %d) implements Comparable and returns 0 when compared to element (%d, %d)", classNumber, j, classNumber, i));
                    }
                }
            }
            ++classNumber;
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
    private static void compareShouldReturn0(Object e1, Object e2, String message)
    {
        if (e1 instanceof Comparable<?>) {
            assertTrue(((Comparable<Object>) e1).compareTo(e2) == 0, message);
        }
    }

    @SuppressWarnings("unchecked")
    private static void compareShouldNotReturn0(Object e1, Object e2, String message)
    {
        if (e1 instanceof Comparable<?>) {
            assertFalse(((Comparable<Object>) e1).compareTo(e2) == 0, message);
        }
    }

}