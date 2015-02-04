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

import static io.airlift.stats.cardinality.Utils.computeIndex;
import static io.airlift.stats.cardinality.Utils.computeValue;
import static io.airlift.stats.cardinality.Utils.numberOfBuckets;

public class TestingHll
{
    private final int indexBitLength;
    private final int[] buckets;

    public TestingHll(int indexBitLength)
    {
        this.indexBitLength = indexBitLength;
        buckets = new int[numberOfBuckets(indexBitLength)];
    }

    public void insertHash(long hash)
    {
        int index = computeIndex(hash, indexBitLength);
        int value = computeValue(hash, indexBitLength);

        buckets[index] = Math.max(buckets[index], value);
    }

    public int[] getBuckets()
    {
        return buckets;
    }
}
