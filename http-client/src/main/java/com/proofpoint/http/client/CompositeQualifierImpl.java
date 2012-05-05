package com.proofpoint.http.client;

import java.lang.annotation.Annotation;
import java.util.Arrays;

@SuppressWarnings("ClassExplicitlyAnnotation")
public class CompositeQualifierImpl
        implements CompositeQualifier
{
    private final Class<?>[] value;

    public static CompositeQualifier compositeQualifier(Class<?>... classes)
    {
        return new CompositeQualifierImpl(classes);
    }

    private CompositeQualifierImpl(Class<?>... classes)
    {
        this.value = classes;
    }

    @Override
    public Class<?>[] value()
    {
        return value;
    }

    @Override
    public Class<? extends Annotation> annotationType()
    {
        return CompositeQualifier.class;
    }

    public int hashCode()
    {
        // This is specified in java.lang.Annotation.
        return (127 * "value".hashCode()) ^ Arrays.hashCode(value);
    }

    public boolean equals(Object o)
    {
        if (!(o instanceof CompositeQualifier)) {
            return false;
        }
        CompositeQualifier other = (CompositeQualifier) o;
        return Arrays.equals(value, other.value());
    }
}
