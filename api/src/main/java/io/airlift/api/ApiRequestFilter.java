package io.airlift.api;

import jakarta.ws.rs.container.ContainerRequestFilter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiRequestFilter
{
    Class<? extends ContainerRequestFilter> value();
}
