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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

public final class TestingClock extends Clock {
    private final ZoneId zone;
    private Instant instant;

    public TestingClock() {
        this(ZoneOffset.UTC);
    }

    public TestingClock(ZoneId zone) {
        this(zone, Instant.ofEpochMilli(1575000618963L));
    }

    private TestingClock(ZoneId zone, Instant instant) {
        this.zone = requireNonNull(zone, "zone is null");
        this.instant = requireNonNull(instant, "instant is null");
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestingClock(zone, instant);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public void increment(long delta, TimeUnit unit) {
        checkArgument(delta >= 0, "delta is negative");
        instant = instant.plusNanos(unit.toNanos(delta));
    }
}
