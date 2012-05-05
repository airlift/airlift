package com.proofpoint.http.client;

import com.google.inject.Binder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;

import java.lang.annotation.Annotation;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.http.client.AsyncHttpClientModule.createAsyncHttpClientModule;
import static com.proofpoint.http.client.HttpClientModule.createHttpClientModule;

public class HttpClientBinder
{
    private final Binder binder;

    private HttpClientBinder(Binder binder)
    {
        this.binder = checkNotNull(binder, "binder is null");
    }

    public static HttpClientBinder httpClientBinder(Binder binder)
    {
        return new HttpClientBinder(binder);
    }

    public HttpClientBindingBuilder bindHttpClient(String name, Class<? extends Annotation> annotation)
    {
        checkNotNull(name, "name is null");
        checkNotNull(annotation, "annotation is null");

        binder.install(createHttpClientModule(name, annotation));
        return createBindingBuilder(HttpClientModule.filterQualifier(annotation));
    }

    public HttpClientBindingBuilder bindAsyncHttpClient(String name, Class<? extends Annotation> annotation)
    {
        checkNotNull(name, "name is null");
        checkNotNull(annotation, "annotation is null");

        binder.install(createAsyncHttpClientModule(name, annotation));
        return createBindingBuilder(AsyncHttpClientModule.filterQualifier(annotation));
    }

    private HttpClientBindingBuilder createBindingBuilder(CompositeQualifier qualifier)
    {
        return new HttpClientBindingBuilder(newSetBinder(binder, HttpRequestFilter.class, qualifier));
    }

    public static class HttpClientBindingBuilder
    {
        private final Multibinder<HttpRequestFilter> multibinder;

        private HttpClientBindingBuilder(Multibinder<HttpRequestFilter> multibinder)
        {
            this.multibinder = multibinder;
        }

        public LinkedBindingBuilder<HttpRequestFilter> addFilterBinding()
        {
            return multibinder.addBinding();
        }

        public HttpClientBindingBuilder withFilter(Class<? extends HttpRequestFilter> filterClass)
        {
            multibinder.addBinding().to(filterClass);
            return this;
        }

        public HttpClientBindingBuilder withTracing()
        {
            return withFilter(TraceTokenRequestFilter.class);
        }
    }
}
