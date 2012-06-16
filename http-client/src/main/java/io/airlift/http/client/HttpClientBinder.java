package io.airlift.http.client;

import com.google.inject.Binder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;

import java.lang.annotation.Annotation;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

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
        return createBindingBuilder(new HttpClientModule(name, annotation));
    }

    public HttpClientBindingBuilder bindAsyncHttpClient(String name, Class<? extends Annotation> annotation)
    {
        checkNotNull(name, "name is null");
        checkNotNull(annotation, "annotation is null");
        return createBindingBuilder(new AsyncHttpClientModule(name, annotation));
    }

    private HttpClientBindingBuilder createBindingBuilder(AbstractHttpClientModule module)
    {
        binder.install(module);
        return new HttpClientBindingBuilder(module,
                newSetBinder(binder, HttpRequestFilter.class, module.getFilterQualifier()));
    }

    public static class HttpClientBindingBuilder
    {
        private final AbstractHttpClientModule module;
        private final Multibinder<HttpRequestFilter> multibinder;

        private HttpClientBindingBuilder(AbstractHttpClientModule module, Multibinder<HttpRequestFilter> multibinder)
        {
            this.module = module;
            this.multibinder = multibinder;
        }

        public HttpClientBindingBuilder withAlias(Class<? extends Annotation> alias)
        {
            module.addAlias(alias);
            return this;
        }

        public HttpClientBindingBuilder withAliases(Collection<Class<? extends Annotation>> aliases)
        {
            for (Class<? extends Annotation> annotation : aliases) {
                module.addAlias(annotation);
            }
            return this;
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
