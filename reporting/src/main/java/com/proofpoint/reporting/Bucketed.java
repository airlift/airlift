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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.proofpoint.reporting.BucketIdProvider;

public abstract class Bucketed<T>
{
    private static final BucketIdProvider INITIAL_BUCKET_ID_PROVIDER = new BucketIdProvider()
    {
        @Override
        public int get()
        {
            return -5;
        }
    };
    private BucketIdProvider bucketIdProvider = INITIAL_BUCKET_ID_PROVIDER;
    private int currentBucketId = -10;
    private T previousBucket = null;
    private T currentBucket = null;

    protected abstract T createBucket();

    protected final synchronized <R> R applyToCurrentBucket(Function<T, R> function)
    {
        rotateBucketIfNeeded();
        return function.apply(currentBucket);
    }

    @SuppressWarnings("UnusedDeclaration") // Called via reflection
    private synchronized T getPreviousBucket()
    {
        rotateBucketIfNeeded();
        return previousBucket;
    }

    @VisibleForTesting
    public synchronized void setBucketIdProvider(BucketIdProvider bucketIdProvider)
    {
        this.bucketIdProvider = bucketIdProvider;
        currentBucketId = bucketIdProvider.get();
        currentBucket = createBucket();
        previousBucket = createBucket();
    }

    private void rotateBucketIfNeeded()
    {
        int bucketId = bucketIdProvider.get();
        if (bucketId != currentBucketId) {
            if (currentBucketId + 1 == bucketId) {
                previousBucket = currentBucket;
            }
            else {
                previousBucket = createBucket();
            }
            currentBucketId = bucketId;
            currentBucket = createBucket();
        }
    }
}
