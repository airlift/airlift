package io.airlift.discovery.client;

import com.google.common.base.Preconditions;

import java.lang.annotation.Annotation;

class ServiceTypeImpl
        implements ServiceType
{
    private final String value;

    public ServiceTypeImpl(String value)
    {
        Preconditions.checkNotNull(value, "value is null");
        this.value = value;
    }

    public String value()
    {
        return value;
    }

    public String toString()
    {
        return String.format("@%s(value=%s)", ServiceType.class.getName(), value);
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
        return result;
    }

    public Class<? extends Annotation> annotationType()
    {
        return ServiceType.class;
    }
}
