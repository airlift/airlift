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

import io.airlift.stats.TimeStat;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.concurrent.atomic.AtomicLong;

public final class ManagedDataSourceStats
{
    private final TimeStat checkout = new TimeStat();
    private final TimeStat create = new TimeStat();
    private final TimeStat held = new TimeStat();
    private final AtomicLong connectionErrorCount = new AtomicLong();
    private final AtomicLong creationErrorCount = new AtomicLong();

    @Managed
    @Nested
    public TimeStat getCheckout()
    {
        return checkout;
    }

    @Managed
    @Nested
    public TimeStat getCreate()
    {
        return create;
    }

    @Managed
    @Nested
    public TimeStat getHeld()
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
        checkout.add(elapsedTime);
    }

    void connectionCreated(Duration elapsedTime)
    {
        create.add(elapsedTime);
    }

    void connectionReturned(Duration elapsedTime)
    {
        held.add(elapsedTime);
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
