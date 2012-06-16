/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.dbpool;

import io.airlift.stats.TimedStat;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.concurrent.atomic.AtomicLong;

public final class ManagedDataSourceStats
{
    private final TimedStat checkout = new TimedStat();
    private final TimedStat create = new TimedStat();
    private final TimedStat held = new TimedStat();
    private final AtomicLong connectionErrorCount = new AtomicLong();
    private final AtomicLong creationErrorCount = new AtomicLong();

    @Managed
    @Nested
    public TimedStat getCheckout()
    {
        return checkout;
    }

    @Managed
    @Nested
    public TimedStat getCreate()
    {
        return create;
    }

    @Managed
    @Nested
    public TimedStat getHeld()
    {
        return held;
    }

    @Managed
    public long getConnectionErrorCount()
    {
        return connectionErrorCount.get();
    }

    @Managed
    public long getCreationErrorCount()
    {
        return creationErrorCount.get();
    }

    void connectionCheckedOut(Duration elapsedTime)
    {
        checkout.addValue(elapsedTime);
    }

    void connectionCreated(Duration elapsedTime)
    {
        create.addValue(elapsedTime);
    }

    void connectionReturned(Duration elapsedTime)
    {
        held.addValue(elapsedTime);
    }

    void creationErrorOccurred()
    {
        creationErrorCount.incrementAndGet();
    }

    void connectionErrorOccurred()
    {
        connectionErrorCount.incrementAndGet();
    }
}
