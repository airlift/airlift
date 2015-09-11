/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.stats;

import com.proofpoint.reporting.Bucketed;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;

public class TestMaxGauge
{
    private TestingBucketIdProvider bucketIdProvider;

    @BeforeMethod
    public void setup()
    {
        bucketIdProvider = new TestingBucketIdProvider();
    }

    @Test
    public void testWithBucketIdProvider()
            throws Exception
    {
        MaxGauge maxGauge = new MaxGauge();
        maxGauge.setBucketIdProvider(bucketIdProvider);
        maxGauge.update(1);
        assertPreviousMax(maxGauge, 0);
        ++bucketIdProvider.id;
        maxGauge.update(3);
        maxGauge.update(2);
        assertPreviousMax(maxGauge, 1);
        ++bucketIdProvider.id;
        assertPreviousMax(maxGauge, 3);
        ++bucketIdProvider.id;
        assertPreviousMax(maxGauge, 2);
    }

    @Test
    public void testInstantaneous()
            throws Exception
    {
        MaxGauge maxGauge = new MaxGauge();
        maxGauge.setBucketIdProvider(bucketIdProvider);
        maxGauge.updateInstantaneous(1);
        assertPreviousMax(maxGauge, 0);
        ++bucketIdProvider.id;
        maxGauge.updateInstantaneous(3);
        maxGauge.updateInstantaneous(2);
        assertPreviousMax(maxGauge, 1);
        ++bucketIdProvider.id;
        assertPreviousMax(maxGauge, 3);
        ++bucketIdProvider.id;
        assertPreviousMax(maxGauge, 0);
    }

    private static void assertPreviousMax(MaxGauge maxGauge, long expected)
            throws Exception
    {
        Method method = Bucketed.class.getDeclaredMethod("getPreviousBucket");
        method.setAccessible(true);
        MaxGauge.Bucket previousBucket = (MaxGauge.Bucket) method.invoke(maxGauge);
        assertEquals(previousBucket.getMax(), expected);
    }
}
