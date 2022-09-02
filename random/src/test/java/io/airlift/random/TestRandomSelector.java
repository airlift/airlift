/*
 * Copyright Starburst Data, Inc. All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF STARBURST DATA.
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 *
 * Redistribution of this material is strictly prohibited.
 */
package io.airlift.random;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class TestRandomSelector
{
    @Test
    public void testWeightedRandomSelector()
    {
        List<Integer> objects = List.of(1, 2, 3, 4, 5, 6);
        Map<Integer, Double> weights = Map.of(
                1, 10.0,
                2, 20.0,
                3, 10.0,
                4, 50.0,
                5, 10.0,
                6, 1.0);

        double total = weights.values().stream().mapToDouble(value -> value).sum();

        RandomSelector<Integer> selector = new WeightedRandomSelector<>(
                objects,
                weights::get,
                sequenceGenerator(total, 9.99, 10.0, 10.1, 89.9, 90.0, 90.1));

        assertThat(selector.next()).isEqualTo(1);
        assertThat(selector.next()).isEqualTo(1);
        assertThat(selector.next()).isEqualTo(2);
        assertThat(selector.next()).isEqualTo(4);
        assertThat(selector.next()).isEqualTo(4);
        assertThat(selector.next()).isEqualTo(5);
    }

    private static SequenceRandomGenerator sequenceGenerator(double total, double... sequence)
    {
        return new SequenceRandomGenerator(total, sequence);
    }

    private static class SequenceRandomGenerator
            implements DoubleSupplier
    {
        private final double[] sequence;
        private final double total;
        private int index;

        public SequenceRandomGenerator(double total, double[] sequence)
        {
            this.total = total;
            requireNonNull(sequence, "sequence is null");
            checkArgument(sequence.length > 0, "sequence cannot be empty");
            this.sequence = sequence.clone();
        }

        @Override
        public double getAsDouble()
        {
            if (index == sequence.length) {
                throw new RuntimeException("no more entries in sequence");
            }
            return sequence[index++] / total;
        }
    }
}
