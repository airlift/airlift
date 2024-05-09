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
package io.airlift.http.server.jetty;

import io.airlift.http.server.DoubleSummaryStats;
import io.airlift.units.Duration;

import java.time.Instant;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

public record RequestTiming(
        Instant requestStarted,
        Duration timeToDispatch,
        Duration timeToHandling,
        Duration timeToFirstByte,
        Duration timeToLastByte,
        Duration timeToCompletion,
        DoubleSummaryStats responseContentInterarrivalStats)
{
    public RequestTiming
    {
        requireNonNull(requestStarted, "requestStarted is null");
        verifyTimeIncreasing("dispatch to handling", timeToDispatch, timeToHandling);
        verifyTimeIncreasing("handling to first byte", timeToHandling, timeToFirstByte);
        verifyTimeIncreasing("first byte to last byte", timeToFirstByte, timeToLastByte);
        verifyTimeIncreasing("dispatch to completion", timeToDispatch, timeToCompletion);
    }

    private void verifyTimeIncreasing(String description, Duration from, Duration to)
    {
        verify(from.compareTo(to) <= 0, "Expected time from %s to increase but got: %s to %s", description, from, to);
    }
}
