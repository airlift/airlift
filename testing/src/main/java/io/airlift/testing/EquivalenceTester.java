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
 * <p>
 * Licensed under Apache License, Version 2.0
 */

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
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
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Equivalence tester streamlining tests of {@link #equals(Object)} and {@link #hashCode} methods. Using this tester makes it
 * easy to verify that {@link #equals(Object)} is indeed an <a href="http://en.wikipedia.org/wiki/Equivalence_relation">equivalence
 * relation</a> (reflexive, symmetric and transitive). It also verifies that equality between two objects implies hash
 * code equality, as required by the {@link #hashCode()} contract.
 */
public final class EquivalenceTester
{
    private EquivalenceTester() {}

    @Deprecated
    public static void check(Collection<?>... equivalenceClasses)
    {
        EquivalenceCheck<Object> tester = equivalenceTester();
        for (Collection<?> equivalenceClass : equivalenceClasses) {
            tester.addEquivalentGroup(equivalenceClass);
        }
        tester.check();
    }

    public static <T> EquivalenceCheck<T> equivalenceTester()
    {
        return new EquivalenceCheck<>();
    }

    public static class EquivalenceCheck<T>
    {
        private final List<List<T>> equivalenceClasses = new ArrayList<>();

        private EquivalenceCheck()
        {
        }

        @SafeVarargs
        public final EquivalenceCheck<T> addEquivalentGroup(T value, T... moreValues)
        {
            equivalenceClasses.add(Lists.asList(value, moreValues));
            return this;
        }

        public EquivalenceCheck<T> addEquivalentGroup(Iterable<T> objects)
        {
            equivalenceClasses.add(newArrayList(objects));
            return this;
        }

        public void check()
        {
            List<ElementCheckFailure> failures = checkEquivalence();

            if (!failures.isEmpty()) {
                throw new EquivalenceAssertionError(failures);
            }
        }

        @SuppressWarnings("ObjectEqualsNull")
        private List<ElementCheckFailure> checkEquivalence()
        {
            ImmutableList.Builder<ElementCheckFailure> errors = new ImmutableList.Builder<>();

            //
            // equal(null)
            //
            int classNumber = 0;
            for (Collection<?> congruenceClass : equivalenceClasses) {
                int elementNumber = 0;
                for (Object element : congruenceClass) {
                    // nothing can be equal to null
                    try {
                        if (element.equals(null)) {
                            errors.add(new ElementCheckFailure(EQUAL_TO_NULL, classNumber, elementNumber, element));
                        }
                    }
                    catch (NullPointerException e) {
                        errors.add(new ElementCheckFailure(EQUAL_NULL_EXCEPTION, classNumber, elementNumber, element));
                    }

                    // if a class implements comparable, object.compareTo(null) must throw NPE
                    if (element instanceof Comparable) {
                        try {
                            ((Comparable<?>) element).compareTo(null);
                            errors.add(new ElementCheckFailure(COMPARE_EQUAL_TO_NULL, classNumber, elementNumber, element));
                        }
                        catch (NullPointerException e) {
                            // ok
                        }
                    }

                    // nothing can be equal to object of an unrelated class
                    try {
                        if (element.equals(new OtherClass())) {
                            errors.add(new ElementCheckFailure(EQUAL_TO_UNRELATED_CLASS, classNumber, elementNumber, element));
                        }
                    }
                    catch (ClassCastException e) {
                        errors.add(new ElementCheckFailure(EQUAL_TO_UNRELATED_CLASS_CLASS_CAST_EXCEPTION, classNumber, elementNumber, element));
                    }

                    ++elementNumber;
                }

                ++classNumber;
            }

            //
            // reflexivity
            //
            classNumber = 0;
            for (Collection<?> congruenceClass : equivalenceClasses) {
                int elementNumber = 0;
                for (Object element : congruenceClass) {
                    if (!element.equals(element)) {
                        errors.add(new ElementCheckFailure(NOT_REFLEXIVE, classNumber, elementNumber, element));
                    }
                    if (!doesCompareReturn0(element, element)) {
                        errors.add(new ElementCheckFailure(COMPARE_NOT_REFLEXIVE, classNumber, elementNumber, element));
                    }
                    ++elementNumber;
                }
                ++classNumber;
            }

            //
            // equality within congruence classes
            //
            classNumber = 0;
            for (List<?> congruenceClass : equivalenceClasses) {
                for (int primaryElementNumber = 0; primaryElementNumber < congruenceClass.size(); primaryElementNumber++) {
                    Object primary = congruenceClass.get(primaryElementNumber);
                    for (int secondaryElementNumber = primaryElementNumber + 1; secondaryElementNumber < congruenceClass.size(); secondaryElementNumber++) {
                        Object secondary = congruenceClass.get(secondaryElementNumber);
                        if (!primary.equals(secondary)) {
                            errors.add(new PairCheckFailure(NOT_EQUAL, classNumber, primaryElementNumber, primary, classNumber, secondaryElementNumber, secondary));
                        }
                        if (!secondary.equals(primary)) {
                            errors.add(new PairCheckFailure(NOT_EQUAL, classNumber, secondaryElementNumber, secondary, classNumber, primaryElementNumber, primary));
                        }
                        try {
                            if (!doesCompareReturn0(primary, secondary)) {
                                errors.add(new PairCheckFailure(COMPARE_NOT_EQUAL,
                                        classNumber,
                                        primaryElementNumber,
                                        primary,
                                        classNumber,
                                        secondaryElementNumber,
                                        secondary));
                            }
                        }
                        catch (ClassCastException e) {
                            errors.add(new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, classNumber, primaryElementNumber, primary, classNumber, secondaryElementNumber, secondary));
                        }
                        try {
                            if (!doesCompareReturn0(secondary, primary)) {
                                errors.add(new PairCheckFailure(COMPARE_NOT_EQUAL,
                                        classNumber,
                                        secondaryElementNumber,
                                        secondary,
                                        classNumber,
                                        primaryElementNumber,
                                        primary));
                            }
                        }
                        catch (ClassCastException e) {
                            errors.add(new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION, classNumber, secondaryElementNumber, secondary, classNumber, primaryElementNumber, primary));
                        }
                        if (primary.hashCode() != secondary.hashCode()) {
                            errors.add(new PairCheckFailure(HASH_CODE_NOT_SAME, classNumber, primaryElementNumber, primary, classNumber, secondaryElementNumber, secondary));
                        }
                    }
                }
                ++classNumber;
            }

            //
            // inequality across congruence classes
            //
            for (int primaryClassNumber = 0; primaryClassNumber < equivalenceClasses.size(); primaryClassNumber++) {
                List<?> primaryCongruenceClass = equivalenceClasses.get(primaryClassNumber);
                for (int secondaryClassNumber = primaryClassNumber + 1; secondaryClassNumber < equivalenceClasses.size(); secondaryClassNumber++) {
                    List<?> secondaryCongruenceClass = equivalenceClasses.get(secondaryClassNumber);
                    int primaryElementNumber = 0;
                    for (Object primary : primaryCongruenceClass) {
                        int secondaryElementNumber = 0;
                        for (Object secondary : secondaryCongruenceClass) {
                            if (primary.equals(secondary)) {
                                errors.add(new PairCheckFailure(EQUAL, primaryClassNumber, primaryElementNumber, primary, secondaryClassNumber, secondaryElementNumber, secondary));
                            }
                            if (secondary.equals(primary)) {
                                errors.add(new PairCheckFailure(EQUAL, secondaryClassNumber, secondaryElementNumber, secondary, primaryClassNumber, primaryElementNumber, primary));
                            }
                            try {
                                if (!doesCompareNotReturn0(primary, secondary)) {
                                    errors.add(new PairCheckFailure(COMPARE_EQUAL, primaryClassNumber, primaryElementNumber, primary, secondaryClassNumber, secondaryElementNumber, secondary));
                                }
                            }
                            catch (ClassCastException e) {
                                errors.add(new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION,
                                        primaryClassNumber,
                                        primaryElementNumber,
                                        primary,
                                        secondaryClassNumber,
                                        secondaryElementNumber,
                                        secondary));
                            }
                            try {
                                if (!doesCompareNotReturn0(secondary, primary)) {
                                    errors.add(new PairCheckFailure(COMPARE_EQUAL, secondaryClassNumber, secondaryElementNumber, secondary, primaryClassNumber, primaryElementNumber, primary));
                                }
                            }
                            catch (ClassCastException e) {
                                errors.add(new PairCheckFailure(COMPARE_CLASS_CAST_EXCEPTION,
                                        secondaryClassNumber,
                                        secondaryElementNumber,
                                        secondary,
                                        primaryClassNumber,
                                        primaryElementNumber,
                                        primary));
                            }
                            secondaryElementNumber++;
                        }
                        primaryElementNumber++;
                    }
                }
            }

            return errors.build();
        }

        @SuppressWarnings("unchecked")
        private static <T> boolean doesCompareReturn0(T e1, T e2)
        {
            if (!(e1 instanceof Comparable<?>)) {
                return true;
            }

            Comparable<T> comparable = (Comparable<T>) e1;
            return comparable.compareTo(e2) == 0;
        }

        @SuppressWarnings("unchecked")
        private static <T> boolean doesCompareNotReturn0(T e1, T e2)
        {
            if (!(e1 instanceof Comparable<?>)) {
                return true;
            }

            Comparable<T> comparable = (Comparable<T>) e1;
            return comparable.compareTo(e2) != 0;
        }

        private static class OtherClass
        {
        }
    }

    @SafeVarargs
    @Deprecated
    public static <T extends Comparable<T>> void checkComparison(Iterable<T> initialGroup, Iterable<T> greaterGroup, Iterable<T>... moreGreaterGroup)
    {
        ComparisonCheck<T> tester = comparisonTester()
                .addLesserGroup(initialGroup)
                .addGreaterGroup(greaterGroup);

        for (Iterable<T> equivalenceClass : moreGreaterGroup) {
            tester.addGreaterGroup(equivalenceClass);
        }
        tester.check();
    }

    public static InitialComparisonCheck comparisonTester()
    {
        return new InitialComparisonCheck();
    }

    public static class InitialComparisonCheck
    {
        private InitialComparisonCheck()
        {
        }

        @SafeVarargs
        public final <T extends Comparable<T>> ComparisonCheck<T> addLesserGroup(T value, T... moreValues)
        {
            ComparisonCheck<T> comparisonCheck = new ComparisonCheck<>();
            comparisonCheck.addGreaterGroup(Lists.asList(value, moreValues));
            return comparisonCheck;
        }

        public <T extends Comparable<T>> ComparisonCheck<T> addLesserGroup(Iterable<T> objects)
        {
            ComparisonCheck<T> comparisonCheck = new ComparisonCheck<>();
            comparisonCheck.addGreaterGroup(objects);
            return comparisonCheck;
        }
    }

    public static class ComparisonCheck<T extends Comparable<T>>
    {
        private final EquivalenceCheck<T> equivalence = new EquivalenceCheck<>();

        private ComparisonCheck()
        {
        }

        @SafeVarargs
        public final ComparisonCheck<T> addGreaterGroup(T value, T... moreValues)
        {
            equivalence.addEquivalentGroup(Lists.asList(value, moreValues));
            return this;
        }

        public ComparisonCheck<T> addGreaterGroup(Iterable<T> objects)
        {
            equivalence.addEquivalentGroup(objects);
            return this;
        }

        public void check()
        {
            ImmutableList.Builder<ElementCheckFailure> builder = new ImmutableList.Builder<>();

            builder.addAll(equivalence.checkEquivalence());

            List<List<T>> equivalenceClasses = equivalence.equivalenceClasses;
            for (int lesserClassNumber = 0; lesserClassNumber < equivalenceClasses.size(); lesserClassNumber++) {
                List<T> lesserBag = equivalenceClasses.get(lesserClassNumber);

                for (int greaterClassNumber = lesserClassNumber + 1; greaterClassNumber < equivalenceClasses.size(); greaterClassNumber++) {
                    List<T> greaterBag = equivalenceClasses.get(greaterClassNumber);
                    for (int lesserElementNumber = 0; lesserElementNumber < lesserBag.size(); lesserElementNumber++) {
                        T lesser = lesserBag.get(lesserElementNumber);
                        for (int greaterElementNumber = 0; greaterElementNumber < greaterBag.size(); greaterElementNumber++) {
                            T greater = greaterBag.get(greaterElementNumber);
                            try {
                                if (lesser.compareTo(greater) >= 0) {
                                    builder.add(new PairCheckFailure(NOT_LESS_THAN, lesserClassNumber, lesserElementNumber, lesser, greaterClassNumber, greaterElementNumber, greater));
                                }
                            }
                            catch (ClassCastException e) {
                                // this has already been reported in the checkEquivalence section
                            }
                            try {
                                if (greater.compareTo(lesser) <= 0) {
                                    builder.add(new PairCheckFailure(NOT_GREATER_THAN, greaterClassNumber, greaterElementNumber, greater, lesserClassNumber, lesserElementNumber, lesser));
                                }
                            }
                            catch (ClassCastException e) {
                                // this has already been reported in the checkEquivalence section
                            }
                        }
                    }
                }
            }

            List<ElementCheckFailure> failures = builder.build();
            if (!failures.isEmpty()) {
                throw new EquivalenceAssertionError(failures);
            }
        }
    }

    public static enum EquivalenceFailureType
    {
        EQUAL_TO_NULL("Element (%d, %d):<%s> returns true when compared to null via equals()"),
        EQUAL_NULL_EXCEPTION("Element (%d, %d):<%s> throws NullPointerException when when compared to null via equals()"),
        COMPARE_EQUAL_TO_NULL("Element (%d, %d):<%s> implements Comparable but does not throw NullPointerException when compared to null"),
        EQUAL_TO_UNRELATED_CLASS("Element (%d, %d):<%s> returns true when compared to an unrelated class via equals()"),
        EQUAL_TO_UNRELATED_CLASS_CLASS_CAST_EXCEPTION("Element (%d, %d):<%s> throws a ClassCastException when compared to an unrelated class via equals()"),
        NOT_REFLEXIVE("Element (%d, %d):<%s> is not equal to itself when compared via equals()"),
        COMPARE_NOT_REFLEXIVE("Element (%d, %d):<%s> implements Comparable but compare does not return 0 when compared to itself"),
        NOT_EQUAL("Element (%d, %d):<%s> is not equal to element (%d, %d):<%s> when compared via equals()"),
        COMPARE_NOT_EQUAL("Element (%d, %d):<%s> is not equal to element (%d, %d):<%s> when compared via compareTo(T)"),
        COMPARE_CLASS_CAST_EXCEPTION("Element (%d, %d):<%s> throws a ClassCastException when compared to element (%d, %d):<%s> via compareTo(T)"),
        HASH_CODE_NOT_SAME("Elements (%d, %d):<%s> and (%d, %d):<%s> have different hash codes"),
        EQUAL("Element (%d, %d):<%s> is equal to element (%d, %d):<%s> when compared via equals()"),
        COMPARE_EQUAL("Element (%d, %d):<%s> implements Comparable and returns 0 when compared to element (%d, %d):<%s>"),
        NOT_LESS_THAN("Element (%d, %d):<%s> is not less than (%d, %d):<%s>"),
        NOT_GREATER_THAN("Element (%d, %d):<%s> is not greater than (%d, %d):<%s>"),
        /**/;

        private final String message;

        EquivalenceFailureType(String message)
        {
            this.message = message;
        }

        public String getMessage()
        {
            return message;
        }
    }

    public static class ElementCheckFailure
    {
        protected final EquivalenceFailureType type;
        protected final int primaryClassNumber;
        protected final int primaryElementNumber;
        protected final Object primaryObject;

        public ElementCheckFailure(EquivalenceFailureType type, int primaryClassNumber, int primaryElementNumber, Object primaryObject)
        {
            requireNonNull(type, "type is null");
            this.type = type;
            this.primaryClassNumber = primaryClassNumber;
            this.primaryElementNumber = primaryElementNumber;
            this.primaryObject = primaryObject;
        }

        public EquivalenceFailureType getType()
        {
            return type;
        }

        public int getPrimaryClassNumber()
        {
            return primaryClassNumber;
        }

        public int getPrimaryElementNumber()
        {
            return primaryElementNumber;
        }

        @Override
        public String toString()
        {
            return format(type.getMessage(), primaryClassNumber, primaryElementNumber, primaryObject);
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ElementCheckFailure that = (ElementCheckFailure) o;

            if (primaryClassNumber != that.primaryClassNumber) {
                return false;
            }
            if (primaryElementNumber != that.primaryElementNumber) {
                return false;
            }
            if (!type.equals(that.type)) {
                return false;
            }
            if (primaryObject != that.primaryObject && // Some testing objects not reflexive
                    !primaryObject.equals(that.primaryObject)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = type.hashCode();
            result = 31 * result + primaryClassNumber;
            result = 31 * result + primaryElementNumber;
            result = 31 * result + primaryObject.hashCode();
            return result;
        }
    }

    public static class PairCheckFailure
            extends ElementCheckFailure
    {
        private final int secondaryClassNumber;
        private final int secondaryElementNumber;
        private final Object secondaryObject;

        public PairCheckFailure(EquivalenceFailureType type, int primaryClassNumber, int primaryElementNumber, Object primaryObject, int secondaryClassNumber, int secondaryElementNumber, Object secondaryObject)
        {
            super(type, primaryClassNumber, primaryElementNumber, primaryObject);
            this.secondaryClassNumber = secondaryClassNumber;
            this.secondaryElementNumber = secondaryElementNumber;
            this.secondaryObject = secondaryObject;
        }

        public int getSecondaryClassNumber()
        {
            return secondaryClassNumber;
        }

        public int getSecondaryElementNumber()
        {
            return secondaryElementNumber;
        }

        @Override
        public String toString()
        {
            return format(type.getMessage(), primaryClassNumber, primaryElementNumber, primaryObject, secondaryClassNumber, secondaryElementNumber, secondaryObject);
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }

            PairCheckFailure that = (PairCheckFailure) o;

            if (primaryClassNumber != that.primaryClassNumber) {
                return false;
            }
            if (primaryElementNumber != that.primaryElementNumber) {
                return false;
            }
            if (primaryObject != that.primaryObject && // Some testing objects not reflexive
                    !primaryObject.equals(that.primaryObject)) {
                return false;
            }
            if (secondaryClassNumber != that.secondaryClassNumber) {
                return false;
            }
            if (secondaryElementNumber != that.secondaryElementNumber) {
                return false;
            }
            if (secondaryObject != that.secondaryObject && // Some testing objects not reflexive
                    !secondaryObject.equals(that.secondaryObject)) {
                return false;
            }
            if (!type.equals(that.type)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = super.hashCode();
            result = 31 * result + type.hashCode();
            result = 31 * result + primaryClassNumber;
            result = 31 * result + primaryElementNumber;
            result = 31 * result + primaryObject.hashCode();
            result = 31 * result + secondaryClassNumber;
            result = 31 * result + secondaryElementNumber;
            result = 31 * result + secondaryObject.hashCode();
            return result;
        }
    }
}
