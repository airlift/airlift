package com.proofpoint.experimental.discovery.client;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({FIELD, PARAMETER, METHOD})
@BindingAnnotation
public @interface ServiceType
{
    static final String DEFAULT_POOL = "general";

    String value();
    String pool() default DEFAULT_POOL;
}
