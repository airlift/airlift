package com.proofpoint.platform.sample;

import com.google.common.base.Preconditions;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.LegacyConfig;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;

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
        Preconditions.checkNotNull(ttl, "ttl must not be null"); // TODO: remove once configuration supports bean validation
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
