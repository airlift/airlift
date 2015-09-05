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
package com.proofpoint.testing;

import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

public class TestTestingTicker
{
    @Test
    public void testElapseTime()
    {
        TestingTicker ticker = new TestingTicker();
        long startTime = ticker.read();

        ticker.elapseTime(2, SECONDS);
        assertEquals(ticker.read() - startTime, 2_000_000_000);
    }

    @Test
    public void testElapseTimeNanosecondBefore()
    {
        TestingTicker ticker = new TestingTicker();
        long startTime = ticker.read();

        ticker.elapseTimeNanosecondBefore(2, SECONDS);
        assertEquals(ticker.read() - startTime, 1_999_999_999);
    }
}
