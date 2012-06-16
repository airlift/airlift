package com.proofpoint.event.client;

import static java.lang.String.format;

// TODO move to discovery client?
public class ServiceUnavailableException
    extends RuntimeException
{
    private final String service;
    private final String pool;

    public ServiceUnavailableException(String type, String pool)
    {
        super(format("Service type=[%s], pool=[%s] is not available", type, pool));
        this.service = type;
        this.pool = pool;
    }

    public String getType()
    {
        return service;
    }

    public String getPool()
    {
        return pool;
    }
}
