package io.airlift.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiParameter
{
    /**
     * The exact HTTP header name to publish and bind for an {@link ApiHeader} parameter, for example
     * {@code Idempotency-Key}. When empty, the header name is derived from the parameter name with an
     * {@code X-} prefix. Only valid on {@link ApiHeader} parameters.
     */
    String name() default "";

    String description() default "";

    String[] allowedValues() default {};
}
