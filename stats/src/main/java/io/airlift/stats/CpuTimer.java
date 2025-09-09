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
package io.airlift.stats;

import com.google.common.base.Ticker;
import io.airlift.units.Duration;
import jakarta.annotation.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class CpuTimer
{
    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
    private static final Duration ZERO_NANOS = new Duration(0, NANOSECONDS);

    private final Ticker ticker;
    private final long wallStartTime;
    private final long cpuStartTime;
    private final long userStartTime;

    private long intervalWallStart;
    private long intervalCpuStart;
    private long intervalUserStart;

    public CpuTimer()
    {
        this(Ticker.systemTicker(), true);
    }

    public CpuTimer(boolean collectUserTime)
    {
        this(Ticker.systemTicker(), collectUserTime);
    }

    public CpuTimer(Ticker ticker, boolean collectUserTime)
    {
        this.ticker = requireNonNull(ticker, "ticker is null");
        wallStartTime = ticker.read();
        cpuStartTime = THREAD_MX_BEAN.getCurrentThreadCpuTime();
        // ThreadMXBean will return -1 if user CPU time collection is not supported
        userStartTime = collectUserTime ? THREAD_MX_BEAN.getCurrentThreadUserTime() : -1;

        intervalWallStart = wallStartTime;
        intervalCpuStart = cpuStartTime;
        intervalUserStart = userStartTime;
    }

    public long getWallStartTimeNanos()
    {
        return wallStartTime;
    }

    public long getIntervalWallStartNanos()
    {
        return intervalWallStart;
    }

    public CpuDuration startNewInterval()
    {
        long currentWallTime = ticker.read();
        long currentCpuTime = THREAD_MX_BEAN.getCurrentThreadCpuTime();
        // ThreadMXBean will return -1 if user CPU time collection is not supported
        long currentUserTime = intervalUserStart == -1 ? -1 : THREAD_MX_BEAN.getCurrentThreadUserTime();

        CpuDuration cpuDuration = new CpuDuration(
                nanosBetween(intervalWallStart, currentWallTime),
                nanosBetween(intervalCpuStart, currentCpuTime),
                // currentUserTime is -1 when ThreadMXBean does not support user collection or when collectUserTime is false
                currentUserTime == -1 ? null : nanosBetween(intervalUserStart, currentUserTime));

        intervalWallStart = currentWallTime;
        intervalCpuStart = currentCpuTime;
        intervalUserStart = currentUserTime;

        return cpuDuration;
    }

    public CpuDuration elapsedIntervalTime()
    {
        long currentWallTime = ticker.read();
        long currentCpuTime = THREAD_MX_BEAN.getCurrentThreadCpuTime();
        // ThreadMXBean will return -1 if user CPU time collection is not supported
        long currentUserTime = intervalUserStart == -1 ? -1 : THREAD_MX_BEAN.getCurrentThreadUserTime();

        return new CpuDuration(
                nanosBetween(intervalWallStart, currentWallTime),
                nanosBetween(intervalCpuStart, currentCpuTime),
                // currentUserTime is -1 when ThreadMXBean does not support user collection or when collectUserTime is false
                currentUserTime == -1 ? null : nanosBetween(intervalUserStart, currentUserTime));
    }

    public CpuDuration elapsedTime()
    {
        long currentWallTime = ticker.read();
        long currentCpuTime = THREAD_MX_BEAN.getCurrentThreadCpuTime();
        // ThreadMXBean will return -1 if user CPU time collection is not supported
        long currentUserTime = userStartTime == -1 ? -1 : THREAD_MX_BEAN.getCurrentThreadUserTime();

        return new CpuDuration(
                nanosBetween(wallStartTime, currentWallTime),
                nanosBetween(cpuStartTime, currentCpuTime),
                // currentUserTime is -1 when ThreadMXBean does not support user collection or when collectUserTime is false
                currentUserTime == -1 ? null : nanosBetween(userStartTime, currentUserTime));
    }

    private static Duration nanosBetween(long start, long end)
    {
        return new Duration(Math.abs(end - start), NANOSECONDS);
    }

    public record CpuDuration(Duration wall, Duration cpu, @Nullable Duration user)
    {
        public CpuDuration()
        {
            this(ZERO_NANOS, ZERO_NANOS, ZERO_NANOS);
        }

        public boolean hasUser()
        {
            return user != null;
        }

        /**
         * This method will report zero duration when no user time was collected. Check {@link CpuDuration#hasUser()} or use {@link CpuDuration#userIfPresent()}
         * in order distinguish a true zero user CPU time from no value being present.
         *
         * @return The {@link CpuDuration#user} value if present, otherwise returns a value of zero nanoseconds
         */
        @Override
        public Duration user()
        {
            return user == null ? ZERO_NANOS : user;
        }

        public Optional<Duration> userIfPresent()
        {
            return Optional.ofNullable(user);
        }

        public CpuDuration add(CpuDuration cpuDuration)
        {
            return new CpuDuration(
                    addDurations(wall, cpuDuration.wall),
                    addDurations(cpu, cpuDuration.cpu),
                    (user == null || cpuDuration.user == null) ? null : addDurations(user, cpuDuration.user));
        }

        public CpuDuration subtract(CpuDuration cpuDuration)
        {
            return new CpuDuration(
                    subtractDurations(wall, cpuDuration.wall),
                    subtractDurations(cpu, cpuDuration.cpu),
                    (user == null || cpuDuration.user == null) ? null : subtractDurations(user, cpuDuration.user));
        }

        private static Duration addDurations(Duration a, Duration b)
        {
            return new Duration(a.getValue(NANOSECONDS) + b.getValue(NANOSECONDS), NANOSECONDS);
        }

        private static Duration subtractDurations(Duration a, Duration b)
        {
            return new Duration(Math.max(0, a.getValue(NANOSECONDS) - b.getValue(NANOSECONDS)), NANOSECONDS);
        }
    }
}
