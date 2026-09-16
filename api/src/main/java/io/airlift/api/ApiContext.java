package io.airlift.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter annotation as identifying application-supplied context.
 * These parameters are excluded from the public API and request body. The
 * application is responsible for providing their values through Jersey.
 * <p>
 * Context parameter types must be distinct from types handled by Airlift's
 * built-in parameter providers, such as {@link ApiId} and {@link ApiPagination}.
 * Those providers match by Java type and can take precedence over the application's
 * provider. This annotation affects API modeling, not Jersey provider selection;
 * such overlaps are not validated.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiContext {}
