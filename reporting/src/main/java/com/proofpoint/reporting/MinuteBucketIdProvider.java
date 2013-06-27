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
import com.google.inject.Inject;
import com.proofpoint.stats.BucketIdProvider;

public class MinuteBucketIdProvider
    implements BucketIdProvider
{
    private static final long ONE_MINUTE_IN_NANOS = 60_000_000_000L;
    private final Ticker ticker;
    private final long initialValue;

    @Inject
    public MinuteBucketIdProvider()
    {
        this(Ticker.systemTicker());
    }

    public MinuteBucketIdProvider(Ticker ticker)
    {
        this.ticker = ticker;
        this.initialValue = ticker.read() - (2 * ONE_MINUTE_IN_NANOS);
    }

    @Override
    public int get()
    {
        return (int) ((ticker.read() - initialValue) / ONE_MINUTE_IN_NANOS);
    }
}
