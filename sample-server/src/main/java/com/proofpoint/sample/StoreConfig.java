package com.proofpoint.sample;

import com.proofpoint.configuration.Config;
import com.proofpoint.stats.Duration;

import javax.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;

public class StoreConfig
{
    private Duration ttl = new Duration(1, TimeUnit.HOURS);

    @Config("store.ttl")
    public void setTtl(Duration ttl)
    {
        this.ttl = ttl;
    }

    @NotNull
    public Duration getTtl()
    {
        return ttl;
    }
}
