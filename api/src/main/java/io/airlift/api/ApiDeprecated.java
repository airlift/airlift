package io.airlift.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiDeprecated
{
    /**
     * Documentation on why it's deprecated, etc.
     */
    String information();

    /**
     * optional - date of deprecation - see DeprecationValidator.dateFormatters for accepted date/time formats
     */
    String deprecationDate() default "";

    /**
     * optional - class that contains the new implementation (newImplementationMethod must also be specified)
     */
    Class<?> newImplementationClass() default Object.class;

    /**
     * optional - method name of the new implementation (newImplementationClass must also be specified)
     */
    String newImplementationMethod() default "";
}
