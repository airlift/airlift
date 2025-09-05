package io.airlift.api;

import io.airlift.http.client.HttpStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiResponse
{
    String name();

    String openApiAlternateName() default "";

    String description();

    HttpStatus status();
}
