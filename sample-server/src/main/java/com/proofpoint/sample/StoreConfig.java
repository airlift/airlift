package com.proofpoint.sample;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.DeprecatedConfig;
import com.proofpoint.stats.Duration;

import javax.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;

public class StoreConfig
{
    private Duration ttl = new Duration(1, TimeUnit.HOURS);

    @Deprecated
    @DeprecatedConfig("store.ttl-in-ms")
    public StoreConfig setTtlInMs(int duration)
    {
        return setTtl(new Duration(duration, TimeUnit.MILLISECONDS));
    }

    @Config("store.ttl")
    public StoreConfig setTtl(Duration ttl)
    {
        this.ttl = ttl;
        return this;
    }

    @NotNull
    public Duration getTtl()
    {
        return ttl;
    }
}
