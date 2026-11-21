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

import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.Slice;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Arrays;

@State(Scope.Thread)
public class BenchmarkDenseHllBaseline
{
    @Param({"4", "12", "16"})
    public int indexBitLength;

    @Param({"1", "8"})
    public int baselineShift;

    @Param({"false", "true"})
    public boolean overflow;

    private Slice serialized;
    private int lastBucket;

    @Setup
    public void setup()
    {
        lastBucket = (1 << indexBitLength) - 1;
        byte[] deltas = new byte[(lastBucket + 1) / 2];
        Arrays.fill(deltas, (byte) (baselineShift * 0x11));
        // Filling the last zero bucket triggers one or several baseline adjustments.
        deltas[deltas.length - 1] &= (byte) 0xF0;
        if (overflow) {
            deltas[0] |= (byte) 0xF0;
        }
        DynamicSliceOutput output = new DynamicSliceOutput(deltas.length + 8)
                .appendByte(Format.DENSE_V2.getTag())
                .appendByte(indexBitLength)
                .appendByte(0)
                .appendBytes(deltas)
                .appendShort(overflow ? 1 : 0);
        if (overflow) {
            output.appendShort(0).appendByte(1);
        }
        serialized = output.slice();
    }

    @Benchmark
    public DenseHll adjustBaseline()
    {
        DenseHll hll = new DenseHll(serialized);
        hll.insert(lastBucket, baselineShift);
        return hll;
    }

    @Benchmark
    public DenseHll deserializeOnly()
    {
        return new DenseHll(serialized);
    }
}
