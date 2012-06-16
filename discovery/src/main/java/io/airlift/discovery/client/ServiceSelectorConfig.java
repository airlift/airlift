package io.airlift.discovery.client;

import io.airlift.configuration.Config;

import javax.validation.constraints.NotNull;

public class ServiceSelectorConfig
{
    public static final String DEFAULT_POOL = "general";

    private String pool = DEFAULT_POOL;

    @NotNull
    public String getPool()
    {
        return pool;
    }

    @Config("pool")
    public ServiceSelectorConfig setPool(String pool)
    {
        this.pool = pool;
        return this;
    }
}
