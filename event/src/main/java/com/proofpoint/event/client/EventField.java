package com.proofpoint.event.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventField
{
    String value() default "";
    EventFieldMapping fieldMapping() default EventFieldMapping.DATA;

    enum EventFieldMapping
    {
        DATA,
        HOST,
        TIMESTAMP,
        UUID
    }
}
