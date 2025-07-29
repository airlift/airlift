package io.airlift.jsonrpc;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
public @interface JsonRpcResult
{
    /*
        Your annotated method should have a {@code JsonRpcResponse} parameter
     */

    String value() default "";
}
