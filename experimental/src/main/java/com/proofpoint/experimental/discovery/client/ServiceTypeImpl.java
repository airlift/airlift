package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;

import java.lang.annotation.Annotation;

class ServiceTypeImpl
        implements ServiceType
{
    private final String value;
    private final String pool;


    public ServiceTypeImpl(String value, String pool)
    {
        Preconditions.checkNotNull(value, "value is null");
        Preconditions.checkNotNull(pool, "pool is null");
        this.value = value;
        this.pool = pool;
    }

    public String value()
    {
        return value;
    }

    public String pool()
    {
        return pool;
    }

    public String toString()
    {
        if (DEFAULT_POOL.equals(pool)) {
            return String.format("@%s(%s)", ServiceType.class.getName(), value);
        }
        else {
            return String.format("@%s(value=%s, pool=%s)", ServiceType.class.getName(), value, pool);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceType)) {
            return false;
        }

        ServiceType that = (ServiceType) o;

        if (!pool.equals(that.pool())) {
            return false;
        }
        if (!value.equals(that.value())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        // see Annotation.hashCode()
        int result = 0;
        result += ((127 * "value".hashCode()) ^ value.hashCode());
        result += ((127 * "pool".hashCode()) ^ pool.hashCode());
        return result;
    }

    public Class<? extends Annotation> annotationType()
    {
        return ServiceType.class;
    }
}