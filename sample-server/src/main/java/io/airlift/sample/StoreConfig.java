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
package io.airlift.sample;

import com.google.common.base.Preconditions;
import io.airlift.configuration.Config;
import io.airlift.configuration.LegacyConfig;
import io.airlift.units.Duration;

import javax.validation.constraints.NotNull;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class StoreConfig
{
    private Duration ttl = new Duration(1, TimeUnit.HOURS);

    @Deprecated
    @LegacyConfig(value = "store.ttl-in-ms", replacedBy = "store.ttl")
    public StoreConfig setTtlInMs(int duration)
    {
        return setTtl(new Duration(duration, TimeUnit.MILLISECONDS));
    }

    @Config("store.ttl")
    public StoreConfig setTtl(Duration ttl)
    {
        requireNonNull(ttl, "ttl must not be null");
        Preconditions.checkArgument(ttl.toMillis() > 0, "ttl must be > 0");

        this.ttl = ttl;
        return this;
    }

    @NotNull
    public Duration getTtl()
    {
        return ttl;
    }
}
