/*
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
package io.airlift.stats;

final class DoubleArrays
{
    private DoubleArrays() {}

    private static final int INSERTION_SORT_THRESHOLD = 16;
    private static final int MEDIAN_OF_NINE_THRESHOLD = 128;

    /**
     * Sorts the {@code keys} in the range {@code [from, to)} into ascending order, permuting
     * {@code values} in lockstep so that each key keeps its companion value.
     * <p>
     * This is a three-way (Bentley-McIlroy) quicksort that moves keys and values directly rather
     * than through an index permutation, so the sort and any later scan of the sorted arrays read
     * contiguously instead of gathering through indirection.
     * <p>
     * Comparisons use the primitive {@code <}, {@code >} and {@code ==} operators rather than
     * {@link Double#compare}: callers guarantee the keys are finite, so the NaN and -0.0 handling
     * of {@code Double.compare} is unnecessary.
     *
     * @param keys the array to sort; must contain only finite values in the range
     * @param values the companion array permuted in lockstep with {@code keys}
     * @param from the index of the first element to sort, inclusive
     * @param to the index of the last element to sort, exclusive
     */
    public static void sort(double[] keys, double[] values, int from, int to)
    {
        int length = to - from;
        if (length < INSERTION_SORT_THRESHOLD) {
            insertionSort(keys, values, from, to);
            return;
        }

        // choose the pivot as the median of three candidates
        int low = from;
        int mid = from + length / 2;
        int high = to - 1;
        if (length > MEDIAN_OF_NINE_THRESHOLD) {
            // for large ranges, take the pseudo-median of nine evenly spaced elements
            int eighth = length / 8;
            low = medianOfThree(keys, low, low + eighth, low + 2 * eighth);
            mid = medianOfThree(keys, mid - eighth, mid, mid + eighth);
            high = medianOfThree(keys, high - 2 * eighth, high - eighth, high);
        }
        mid = medianOfThree(keys, low, mid, high);
        double pivot = keys[mid];

        // partition into [< pivot][== pivot][> pivot], stashing keys equal to the pivot at both
        // ends and moving them back to the middle afterwards
        int equalsLeft = from;
        int left = from;
        int right = to - 1;
        int equalsRight = to - 1;
        while (true) {
            while (left <= right) {
                double leftKey = keys[left];
                if (leftKey > pivot) {
                    break;
                }
                if (leftKey == pivot) {
                    swap(keys, values, equalsLeft++, left);
                }
                left++;
            }
            while (right >= left) {
                double rightKey = keys[right];
                if (rightKey < pivot) {
                    break;
                }
                if (rightKey == pivot) {
                    swap(keys, values, right, equalsRight--);
                }
                right--;
            }
            if (left > right) {
                break;
            }
            swap(keys, values, left++, right--);
        }

        // move the keys equal to the pivot from the ends back into the middle
        int leftEqualsCount = Math.min(equalsLeft - from, left - equalsLeft);
        swap(keys, values, from, left - leftEqualsCount, leftEqualsCount);
        int rightEqualsCount = Math.min(equalsRight - right, to - equalsRight - 1);
        swap(keys, values, left, to - rightEqualsCount, rightEqualsCount);

        // recursively sort the elements that are not equal to the pivot
        int lessCount = left - equalsLeft;
        if (lessCount > 1) {
            sort(keys, values, from, from + lessCount);
        }
        int greaterCount = equalsRight - right;
        if (greaterCount > 1) {
            sort(keys, values, to - greaterCount, to);
        }
    }

    private static void insertionSort(double[] keys, double[] values, int from, int to)
    {
        for (int i = from + 1; i < to; i++) {
            double key = keys[i];
            double value = values[i];
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

    private static int medianOfThree(double[] keys, int first, int second, int third)
    {
        double firstKey = keys[first];
        double secondKey = keys[second];
        double thirdKey = keys[third];
        return firstKey < secondKey
                ? (secondKey < thirdKey ? second : (firstKey < thirdKey ? third : first))
                : (secondKey > thirdKey ? second : (firstKey > thirdKey ? third : first));
    }

    private static void swap(double[] keys, double[] values, int first, int second)
    {
        double key = keys[first];
        keys[first] = keys[second];
        keys[second] = key;
        double value = values[first];
        values[first] = values[second];
        values[second] = value;
    }

    private static void swap(double[] keys, double[] values, int first, int second, int count)
    {
        for (int i = 0; i < count; i++) {
            swap(keys, values, first + i, second + i);
        }
    }
}
