package io.airlift.http.server;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;

@Retention(RUNTIME)
@BindingAnnotation
public @interface EnableLegacyUriCompliance {}
