/*
 * Copyright (C) 2002-2019 Sebastiano Vigna
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
 *
 *
 * For the sorting and binary search code:
 *
 * Copyright (C) 1999 CERN - European Organization for Nuclear Research.
 *
 * Permission to use, copy, modify, distribute and sell this software and
 * its documentation for any purpose is hereby granted without fee,
 * provided that the above copyright notice appear in all copies and that
 * both that copyright notice and this permission notice appear in
 * supporting documentation. CERN makes no representations about the
 * suitability of this software for any purpose. It is provided "as is"
 * without expressed or implied warranty.
 */
package io.airlift.stats;

// Note: this code was forked from fastutil (http://fastutil.di.unimi.it/)
final class DoubleArrays
{
    private DoubleArrays() {}

    private static final int QUICKSORT_NO_REC = 16;
    private static final int QUICKSORT_MEDIAN_OF_9 = 128;

    /**
     * Sorts the specified range of {@code keys} into ascending order, permuting
     * {@code values} in parallel so that each key keeps its companion value.
     *
     * <p>
     * The sorting algorithm is a tuned quicksort adapted from Jon L. Bentley and M.
     * Douglas McIlroy, &ldquo;Engineering a Sort Function&rdquo;, <i>Software:
     * Practice and Experience</i>, 23(11), pages 1249&minus;1265, 1993.
     *
     * <p>
     * Unlike an indirect sort, the keys and their companion values are moved
     * directly, so reads during the sort (and any later sequential scan of the
     * sorted arrays) are contiguous rather than gathered through a permutation.
     *
     * <p>
     * Comparisons use primitive {@code <}/{@code >}/{@code ==} rather than
     * {@link Double#compare}: callers guarantee the keys are finite (no NaN), so
     * the extra NaN/&minus;0.0 handling of {@code Double.compare} is unnecessary.
     *
     * <p>
     * Note that this implementation does not allocate any object, contrarily to the
     * implementation used to sort primitive types in {@link java.util.Arrays},
     * which switches to mergesort on large inputs.
     *
     * @param keys the array to be sorted (must contain only finite values in the range).
     * @param values a companion array permuted in lockstep with {@code keys}.
     * @param from the index of the first element (inclusive) to be sorted.
     * @param to the index of the last element (exclusive) to be sorted.
     */
    @SuppressWarnings("checkstyle:InnerAssignment")
    public static void quickSortDual(final double[] keys, final double[] values, final int from, final int to)
    {
        final int len = to - from;
        // Insertion sort on smallest arrays
        if (len < QUICKSORT_NO_REC) {
            insertionSortDual(keys, values, from, to);
            return;
        }
        // Choose a partition element, v
        int m = from + len / 2;
        int l = from;
        int n = to - 1;
        if (len > QUICKSORT_MEDIAN_OF_9) { // Big arrays, pseudomedian of 9
            int s = len / 8;
            l = med3(keys, l, l + s, l + 2 * s);
            m = med3(keys, m - s, m, m + s);
            n = med3(keys, n - 2 * s, n - s, n);
        }
        m = med3(keys, l, m, n); // Mid-size, med of 3
        final double v = keys[m];
        // Establish Invariant: v* (<v)* (>v)* v*
        int a = from;
        int b = a;
        int c = to - 1;
        int d = c;
        while (true) {
            while (b <= c) {
                final double kb = keys[b];
                if (kb > v) {
                    break;
                }
                if (kb == v) {
                    swap(keys, values, a++, b);
                }
                b++;
            }
            while (c >= b) {
                final double kc = keys[c];
                if (kc < v) {
                    break;
                }
                if (kc == v) {
                    swap(keys, values, c, d--);
                }
                c--;
            }
            if (b > c) {
                break;
            }
            swap(keys, values, b++, c--);
        }
        // Swap partition elements back to middle
        int s;
        s = Math.min(a - from, b - a);
        swap(keys, values, from, b - s, s);
        s = Math.min(d - c, to - d - 1);
        swap(keys, values, b, to - s, s);
        // Recursively sort non-partition-elements
        if ((s = b - a) > 1) {
            quickSortDual(keys, values, from, from + s);
        }
        if ((s = d - c) > 1) {
            quickSortDual(keys, values, to - s, to);
        }
    }

    private static void insertionSortDual(final double[] keys, final double[] values, final int from, final int to)
    {
        for (int i = from; ++i < to; ) {
            final double key = keys[i];
            final double value = values[i];
            int j = i;
            while (j > from && keys[j - 1] > key) {
                keys[j] = keys[j - 1];
                values[j] = values[j - 1];
                j--;
            }
            keys[j] = key;
            values[j] = value;
        }
    }

    private static int med3(final double[] x, final int a, final int b, final int c)
    {
        final double aa = x[a];
        final double bb = x[b];
        final double cc = x[c];
        return aa < bb
                ? (bb < cc ? b : (aa < cc ? c : a))
                : (bb > cc ? b : (aa > cc ? c : a));
    }

    private static void swap(final double[] keys, final double[] values, final int a, final int b)
    {
        final double key = keys[a];
        keys[a] = keys[b];
        keys[b] = key;
        final double value = values[a];
        values[a] = values[b];
        values[b] = value;
    }

    private static void swap(final double[] keys, final double[] values, int a, int b, final int n)
    {
        for (int i = 0; i < n; i++, a++, b++) {
            swap(keys, values, a, b);
        }
    }
}
