/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import org.weakref.jmx.Flatten;
import org.weakref.jmx.Nested;

public class SimpleBucket
{
    boolean bucketedBooleanValue;
    int bucketedIntegerValue;
    final NestedBucket nestedBucket = new NestedBucket();
    final NestedBucket flattenBucket = new NestedBucket();

    @Reported
    public boolean isBucketedBooleanValue()
    {
        return bucketedBooleanValue;
    }

    @Reported
    public int getBucketedIntegerValue()
    {
        return bucketedIntegerValue;
    }

    @Nested
    public NestedBucket getNestedBucket()
    {
        return nestedBucket;
    }

    @Flatten
    public NestedBucket getFlattenBucket()
    {
        return flattenBucket;
    }

    static class NestedBucket
    {
        Boolean bucketedBooleanBoxedValue;
        long bucketedLongValue;

        @Reported
        private Boolean isBucketedBooleanBoxedValue()
        {
            return bucketedBooleanBoxedValue;
        }

        @Reported
        private long getBucketedLongValue()
        {
            return bucketedLongValue;
        }
    }
}
