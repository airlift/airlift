package io.airlift.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiCreate
{
    String description();

    Class<?>[] responses() default {};

    ApiTrait[] traits() default {};

    String[] quotas() default {};

    String openApiAlternateName() default "";
}
