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
package io.airlift.testing;

import org.testng.annotations.Test;

import java.time.Instant;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

public class TestTestingClock
{
    @Test
    public void test()
            throws Exception
    {
        TestingClock clock = new TestingClock();

        Instant instant1 = clock.instant();

        Thread.sleep(10);
        Instant instant2 = clock.instant();

        clock.increment(42, SECONDS);
        Instant instant3 = clock.instant();

        assertEquals(instant2, instant1);
        assertEquals(instant3, instant1.plusSeconds(42));
    }
}
