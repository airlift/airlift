package com.proofpoint.experimental.discovery.client;

import com.proofpoint.configuration.Config;

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
    public void setPool(String pool)
    {
        this.pool = pool;
    }
}
