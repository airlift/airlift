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

import com.google.common.base.Ticker;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;

public class TestMinuteBucketIdProvider
{
    private TestingTicker ticker;

    @BeforeMethod
    public void setup()
    {
        ticker = new TestingTicker();
    }

    @Test
    public void testInitialState()
    {
        assertEquals(new MinuteBucketIdProvider(ticker).get(), 0);
        ticker.advance(27, TimeUnit.HOURS);
        ticker.advance(977_777, TimeUnit.NANOSECONDS);
        assertEquals(new MinuteBucketIdProvider(ticker).get(), 0);
    }

    @Test
    public void testMinuteBoundary()
    {
        ticker.advance(27, TimeUnit.HOURS);
        ticker.advance(977_777, TimeUnit.NANOSECONDS);
        MinuteBucketIdProvider idProvider = new MinuteBucketIdProvider(ticker);
        assertEquals(idProvider.get(), 0, "initial state");
        ticker.advance(59_999_999_999L, TimeUnit.NANOSECONDS);
        assertEquals(idProvider.get(), 0, "before minute boundary");
        ticker.advance(1, TimeUnit.NANOSECONDS);
        assertEquals(idProvider.get(), 1, "on minute boundary");
    }

    private class TestingTicker
        extends Ticker
    {
        long nanos;

        @Override
        public long read()
        {
            return nanos;
        }

        public void advance(long value, TimeUnit timeUnit)
        {
            nanos += timeUnit.toNanos(value);
        }
    }
}
