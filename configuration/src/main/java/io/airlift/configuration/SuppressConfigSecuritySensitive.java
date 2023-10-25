package io.airlift.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Configuration setting was detected to be security sensitive but in fact it's marked explicitly as isn't.
 * Must be on same method as @Config
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SuppressConfigSecuritySensitive
{
}
