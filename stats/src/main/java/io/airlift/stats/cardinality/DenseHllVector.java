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
package io.airlift.stats.cardinality;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorSpecies;

import static jdk.incubator.vector.VectorOperators.ADD;
import static jdk.incubator.vector.VectorOperators.BIT_COUNT;
import static jdk.incubator.vector.VectorOperators.LSHR;

// Loaded only when the application enables jdk.incubator.vector.
final class DenseHllVector
{
    private static final VectorSpecies<Byte> BYTES = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long> LONGS = BYTES.withLanes(long.class);
    private static final long LOWEST_BIT_OF_EACH_DELTA = 0x1111_1111_1111_1111L;

    private DenseHllVector() {}

    static int adjustBaseline(DenseHll hll, byte[] deltas, boolean hasOverflows)
    {
        LongVector nonZeroDeltas = LongVector.zero(LONGS);
        int vectorBuckets = 0;
        int zeroDeltas = 0;
        int slot = 0;
        for (; slot < BYTES.loopBound(deltas.length); slot += BYTES.length()) {
            LongVector word = ByteVector.fromArray(BYTES, deltas, slot).reinterpretAsLongs();
            if (hasOverflows) {
                LongVector maxDeltas = word.and(word.lanewise(LSHR, 2));
                maxDeltas = maxDeltas.and(maxDeltas.lanewise(LSHR, 1)).and(LOWEST_BIT_OF_EACH_DELTA);
                if (!maxDeltas.eq(0).allTrue()) {
                    // Preserve the scalar overflow update/removal order, including malformed inputs.
                    zeroDeltas += hll.adjustBaselineScalar(slot * 2, (slot + BYTES.length()) * 2);
                    continue;
                }
            }

            // All nibbles are positive, so each lane subtracts one from sixteen independent deltas.
            word = word.sub(LOWEST_BIT_OF_EACH_DELTA);
            word.reinterpretAsBytes().intoArray(deltas, slot);
            word = word.or(word.lanewise(LSHR, 2));
            word = word.or(word.lanewise(LSHR, 1)).and(LOWEST_BIT_OF_EACH_DELTA);
            nonZeroDeltas = nonZeroDeltas.add(word.lanewise(BIT_COUNT));
            vectorBuckets += BYTES.length() * 2;
        }
        return zeroDeltas + vectorBuckets - (int) nonZeroDeltas.reduceLanes(ADD) + hll.adjustBaselineScalar(slot * 2, deltas.length * 2);
    }
}
